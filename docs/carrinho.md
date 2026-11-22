# Módulo de Carrinho (`cart`)

Módulo do monolito modular (`br.com.ateliedigital.cart`) que guarda o carrinho de cada usuário autenticado no **Redis**. Toda alteração passa por uma validação de integridade do produto no **catálogo**. A estrutura das rotas segue a `atelie-orders-api`, com adaptações ao padrão deste projeto.

## Decisões de regra de negócio (confirmadas)

| Ponto | Decisão |
|---|---|
| Fonte dos dados do produto | Uma porta `ProductCatalog` com um adaptador HTTP para a `atelie-catalog-api`. A URL fica configurável. |
| Preço | **Snapshot + alerta**: o preço é gravado quando o item é adicionado. Na leitura do carrinho, o preço atual é comparado com o gravado e a diferença é sinalizada. |
| Limites | Até 99 unidades por item e até 50 itens distintos por carrinho (configurável). |
| Multi-loja | Permitido. |
| Itens inválidos | São removidos automaticamente na leitura (`GET`) quando o produto deixou de existir ou foi desativado. |
| Expiração | Nenhuma. O carrinho só some ao ser limpo. |
| Comprar da própria loja | Permitido (a regra não foi pedida). |

## Estrutura

```
cart/
├── CartManagement.java          # API pública p/ outros módulos (ex.: checkout chama clear(keycloakId))
├── config/
│   ├── CartConfiguration.java   # RedisTemplate<String, CartItem> com JSON (Jackson)
│   └── CartProperties.java      # atelie.cart.* (limites, prefixo da chave)
├── controller/CartController.java
├── dto/CartRequests.java, CartResponses.java
├── integration/catalog/
│   ├── ProductCatalog.java      # porta (interface)
│   ├── CatalogHttpClient.java   # adaptador HTTP + validação de integridade
│   ├── CatalogProperties.java   # atelie.catalog.base-url
│   ├── ProductSnapshot.java     # dados validados da variante
│   └── VariantLookup.java       # Found | Missing | Failed
├── mapper/CartMapper.java       # status por item, subtotais e totais
├── model/CartItem.java          # o que vai para o Redis
├── repository/CartRepository.java
└── service/CartService.java     # regras
```

O teste `ModularityTests` (Spring Modulith) confirma que o módulo respeita as fronteiras do monolito.

## Autenticação e vínculo com o usuário

- O JWT do Keycloak é validado pelo `SecurityConfiguration`, que já existia no projeto.
- Todas as rotas exigem `@PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN')")`. O papel `ADMIN` recebe 403.
- O `AccountCompletionInterceptor`, que também já existia, barra com `403 ACCOUNT_INCOMPLETE` quem ainda não completou o cadastro.
- **O dono do carrinho é sempre o `sub` do JWT.** Não existe id de carrinho na URL, então um usuário não consegue ler nem alterar o carrinho de outro.

## Armazenamento no Redis

- Cada usuário tem um hash `cart:{keycloakId}`, com um campo por variante (`productVariantId`). O valor é o `CartItem` serializado em JSON:
  ```json
  {"productVariantId":"101","productId":"10","storeId":"1","quantity":2,"unitPrice":49.90,
   "sku":"CAN-AZUL-M","color":"azul","size":"M","addedAt":"...","updatedAt":"..."}
  ```
- As chaves não têm TTL (`TTL = -1`). No `compose.yaml`, o Redis roda com AOF (`--appendonly yes`) e um volume `redis-data`, para os dados sobreviverem a reinícios.
- **Concorrência:** as escritas que dependem do estado atual (somar quantidade, checar o limite de itens) usam `WATCH/MULTI/EXEC`. Se outra requisição alterar o carrinho no meio, a operação é refeita até 10 vezes, com um pequeno backoff aleatório entre as tentativas. Se ainda assim não conseguir gravar, responde `409`.
- O mapper JSON usado no Redis é independente do mapper da API e ignora campos desconhecidos. Assim, versões antigas ou novas do `CartItem` continuam legíveis.

## Validação de integridade do produto

O `CatalogHttpClient` chama `GET {CATALOG_API_BASE_URL}/products/variations/{id}`, que responde com o `ProductVariationDetail` da catalog-api, e classifica o resultado assim:

| Situação | Resultado |
|---|---|
| 200 com dados consistentes | `Found` (snapshot) |
| 404 | `Missing` |
| `is_active=false` ou `is_deleted=true` (se o catálogo enviar) | `Missing` |
| Id diferente do pedido, sem `product_id`/`store_id`, preço ≤ 0, estoque nulo ou negativo | `Missing` (com log de warning) |
| 5xx, timeout, corpo inválido | `Failed` (estado desconhecido) |

No `GET` do carrinho, as consultas ao catálogo rodam em paralelo (virtual threads).

As outras validações:

- Quantidade (soma do carrinho + pedido) ≤ estoque e ≤ `max-quantity-per-item`.
- Itens distintos ≤ `max-distinct-items`.
- `product_variant_id` aceita apenas `[A-Za-z0-9-]{1,64}`, no body e na URL. Isso impede injetar separadores de chave.

## Rotas

Base: `/api/carts`. O token Bearer é obrigatório. O JSON é em snake_case.

### `GET /api/carts`: consultar o carrinho
Devolve o carrinho conferido com o catálogo:
- **Found:** os dados exibidos vêm do catálogo (preço e estoque atuais). O status do item é calculado assim:
  - `AVAILABLE`: tudo certo.
  - `PRICE_CHANGED`: o preço mudou. `previous_unit_price` traz o preço gravado no snapshot.
  - `INSUFFICIENT_STOCK`: o estoque atual é menor que a quantidade no carrinho. O item **fica fora dos totais**.
- **Missing:** o item é **removido do Redis** e listado em `removed_items` com `reason: PRODUCT_UNAVAILABLE`.
- **Failed:** o item aparece como `UNVERIFIED`, com os dados do snapshot, e **não é removido**. Nesse caso, `verified=false`.

```json
{
  "items": [{"product_variant_id":"101","product_id":"10","store_id":"1","sku":"CAN-AZUL-M",
             "color":"azul","size":"M","quantity":2,"unit_price":59.90,"previous_unit_price":49.90,
             "subtotal":119.80,"available_stock":10,"status":"PRICE_CHANGED",
             "added_at":"...","updated_at":"..."}],
  "total_items": 1, "total_quantity": 2, "total_price": 119.80,
  "verified": true,
  "removed_items": [{"product_variant_id":"102","reason":"PRODUCT_UNAVAILABLE"}]
}
```
Um carrinho vazio responde `200` com totais zerados, sem consultar o catálogo.

### `POST /api/carts/items`: adicionar item
Body: `{"product_variant_id": "101", "quantity": 2}`. A quantidade deve ser maior que 0.
Se a variante já estiver no carrinho, a **quantidade é somada** e o snapshot (preço) é renovado. O `added_at` original é mantido.

| Status | Quando |
|---|---|
| 201 | Item criado ou somado (devolve o item) |
| 400 | Body inválido (quantidade ≤ 0, id ausente ou inválido) |
| 404 | `Product not found` (variante inexistente ou inconsistente) |
| 409 | Estoque insuficiente, limite por item ou limite de itens distintos |
| 503 | Catálogo indisponível |

### `PATCH /api/carts/items/{productVariantId}`: definir quantidade
Body: `{"quantity": 3}`. Define a **quantidade final** do item, sem somar. `0` remove o item.

| Status | Quando |
|---|---|
| 200 | Item atualizado. O preço atual é aceito e o status volta a `AVAILABLE`. |
| 204 | `quantity = 0`: o item foi removido |
| 400 | Quantidade negativa ou id inválido |
| 404 | Item não está no carrinho, ou o produto saiu do catálogo (nesse caso o item é removido) |
| 409 | Estoque ou limite excedido |
| 503 | Catálogo indisponível |

### `DELETE /api/carts/items/{productVariantId}`: remover item
Responde `204` quando remove e `404` se o item não estava no carrinho.

### `DELETE /api/carts`: limpar carrinho
Responde `204` e é idempotente: apaga a chave inteira no Redis.

### Erros comuns a todas as rotas
- `401` sem token.
- `403` para papel não permitido (ex.: `ADMIN`) ou `ACCOUNT_INCOMPLETE`.

O formato do erro é o `ApiError` do `GlobalExceptionHandler`: `timestamp`, `status`, `message`, `path`.

## API pública para outros módulos

`CartManagement.clear(UUID keycloakId)` deve ser chamado pelo futuro checkout ou módulo de pedidos depois que o pedido for criado.

## Configuração

`application.yml`:
```yaml
atelie:
  cart:
    max-quantity-per-item: 99
    max-distinct-items: 50
    key-prefix: "cart:"
  catalog:
    base-url: ${CATALOG_API_BASE_URL:http://localhost:8008/api/v1/catalog}
```
O Redis usa o `spring.data.redis.*` que já existia (`REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`). O `.env.example` ganhou a variável `CATALOG_API_BASE_URL`. O `compose.yaml` ganhou o serviço `redis` (redis:7.4-alpine) e o `pom.xml` ganhou o `spring-boot-starter-data-redis`.

## O que é real e o que é mockado

| Componente | Em produção/dev | Nos testes |
|---|---|---|
| Redis | Real (compose) | **Real**: Testcontainers `redis:7.4-alpine` (`RedisContainerSupport`) |
| Postgres (contas) | Real | **Real**: Testcontainers (`PostgresIntegrationTest`, já existia) |
| Keycloak / JWT | Real | **Mockado**: `SecurityMockMvcRequestPostProcessors.jwt()` via `TestUser` |
| Keycloak Admin, RabbitMQ, ViaCEP | Real | **Mockados** (`@MockitoBean`, já existia) |
| Catálogo (`ProductCatalog`) | **HTTP real para a atelie-catalog-api** (não existe módulo de catálogo no monolito) | **Mockado**: `@MockitoBean` em `RedisIntegrationTest`, com um mapa em memória. No `CatalogHttpClientTest`, as respostas HTTP vêm do `MockRestServiceServer`. |

> O carrinho não funciona sozinho em dev: precisa da `atelie-catalog-api` rodando (porta 8008) ou de um stub. Quando o módulo de catálogo existir no monolito, basta criar outra implementação de `ProductCatalog` que delegue a ele.

## Testes

| Classe | Tipo | Testes | O que cobre |
|---|---|---|---|
| `CartServiceTest` | Unitário (Mockito) | 16 | Regras: soma, limites, estoque, catálogo fora, reconciliação, remoção |
| `CartControllerTest` | MockMvc standalone | 10 | Status HTTP, validação dos DTOs, snake_case |
| `CatalogHttpClientTest` | `MockRestServiceServer` | 7 | 200/404/5xx, inativo, dados inconsistentes, corpo inválido, consulta em lote |
| `CartRepositoryIntegrationTest` | Redis real | 5 | JSON no Redis, ausência de TTL, ordem, isolamento, 8 threads concorrentes sem perder incrementos |
| `CartApiIntegrationTest` | Spring completo + Postgres e Redis reais | 8 | Segurança (401/403/ACCOUNT_INCOMPLETE/ADMIN), fluxo completo, isolamento entre usuários, integridade, reconciliação, catálogo fora |
| `ModularityTests` | Spring Modulith | 1 | Fronteiras entre módulos |

**Resultado de `./mvnw verify`: 226 testes, 0 falhas, 0 erros, BUILD SUCCESS.** O número inclui os testes que já existiam no projeto.

Para rodar só os testes do carrinho:
```bash
./mvnw test -Dtest='Cart*Test,CatalogHttpClientTest,ModularityTests'
```
É preciso ter o Docker rodando, por causa do Testcontainers.

## Como testar manualmente

1. Suba a infraestrutura com `docker compose up -d` (Postgres, Redis, Keycloak, Mailpit). Se a porta 6379 já estiver ocupada, use `REDIS_PORT=6380 docker compose up -d` e exporte o mesmo `REDIS_PORT` para a aplicação.
2. Suba o catálogo: a `atelie-catalog-api` na porta 8008, ou um stub que responda `GET /api/v1/catalog/products/variations/{id}` com `{"id":101,"product_id":10,"store_id":1,"price":49.9,"stock":10,...}`.
3. Rode a aplicação com `./mvnw spring-boot:run`.
4. Obtenha um token (usuários do realm de dev, senha `dev123`):
   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8180/realms/atelie/protocol/openid-connect/token \
     -d grant_type=password -d client_id=atelie-web \
     -d username=cliente@atelie.dev -d password=dev123 | jq -r .access_token)
   ```
5. Na primeira vez, complete o cadastro (`PUT /api/accounts/me/complete`). Sem isso, as rotas respondem `403 ACCOUNT_INCOMPLETE`.
6. Chame as rotas:
   ```bash
   curl -X POST localhost:8080/api/carts/items -H "Authorization: Bearer $TOKEN" \
        -H 'Content-Type: application/json' -d '{"product_variant_id":"101","quantity":2}'
   curl localhost:8080/api/carts -H "Authorization: Bearer $TOKEN"
   ```
7. Também há a pasta **"6. Carrinho (CLIENT/ARTISAN)"** na coleção Postman (`postman/atelie-digital.postman_collection.json`), que usa a nova variável `product_variant_id` (padrão `101`).
8. Para inspecionar o Redis: `redis-cli HGETALL cart:<sub-do-jwt>` e `redis-cli TTL cart:<sub>` (deve dar `-1`).

## Status e pendências

- ✅ Implementação, testes automatizados (226/226) e verificação de modularidade.
- ⚠️ **O teste ponta a ponta contra a aplicação rodando (Keycloak real + stub do catálogo) não foi concluído.** A sessão anterior terminou enquanto o Docker ainda baixava as imagens do Keycloak e do Mailpit. O roteiro está pronto, mas fica no scratchpad da sessão, que pode ser apagado (`catalog_stub.py`, `variants.json`, `e2e.sh`). Convém rodá-lo ou refazer os passos acima manualmente.
- Nenhum commit foi feito.
- Possíveis evoluções: bloquear compra da própria loja; trocar o adaptador HTTP por chamada in-process quando o módulo de catálogo existir; cache curto das consultas ao catálogo.
