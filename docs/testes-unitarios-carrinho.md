# Testes unitários do módulo de carrinho (`cart`)

Este documento é a parte do **Plano de Teste** referente ao módulo de carrinho. Ele segue os itens pedidos na *Descrição do Projeto Integrador*: ferramentas, tipos de teste, casos de teste unitários, seleção dos dados, critérios por técnica, evidências (cobertura e escore de mutação) e relatório de resultados parciais.

As regras de negócio testadas estão descritas em [`carrinho.md`](carrinho.md).

## 1. Ferramentas

| Ferramenta | Uso |
|---|---|
| JUnit 6 (Jupiter) | Execução dos testes. Usa `@Nested` para agrupar por operação e `@ParameterizedTest`/`@CsvSource` para tabelas de valores-limite. |
| Mockito | Dublês do repositório (Redis), do catálogo e do `RedisTemplate`. |
| AssertJ | Asserções. |
| Spring MockMvc (standalone) | Camada HTTP sem subir o contexto Spring, com o `GlobalExceptionHandler` real. |
| `MockRestServiceServer` | Respostas HTTP simuladas da atelie-catalog-api. |
| Hibernate Validator | Validação dos DTOs (Bean Validation) sem Spring. |
| JaCoCo | Cobertura de instruções, ramos e linhas. |
| PIT (pitest) | Teste de mutação e escore de mutação. |
| GitHub Actions | Roda os testes em todo PR para `develop`/`main` e publica os relatórios. |

## 2. Tipos de teste aplicados

| Nível | Classes | Dependências |
|---|---|---|
| **Unitário** (este documento) | `CartServiceTest`, `CartMapperTest`, `CartRepositoryTest`, `CartControllerTest`, `CatalogHttpClientTest`, `CartRequestsValidationTest`, `CartConfigurationTest`, `CartItemTest`, `CartItemStatusTest`, `CartManagementTest`, `ProductCatalogTest` | Nenhuma: não usam Docker, rede ou contexto Spring. |
| Integração | `CartRepositoryIntegrationTest` (Redis real), `CartApiIntegrationTest` (Spring completo + Postgres e Redis reais) | Docker (Testcontainers) |

Nos testes unitários, cada classe é testada isolada das outras:

- `CartService` usa o repositório e o catálogo mockados. O `CartMapper` é real porque é uma função pura, e o relógio (`Clock`) é fixo para as datas serem determinísticas.
- `CartRepository` usa um `RedisTemplate` mockado. O mock executa o `SessionCallback` sobre uma sessão simulada para reproduzir o protocolo `WATCH/MULTI/EXEC`.
- `CartController` usa o serviço mockado e o `GlobalExceptionHandler` real, para conferir o mapeamento de exceção para status HTTP.

## 3. Técnicas, critérios e seleção dos dados de teste

### 3.1 Particionamento em classes de equivalência (caixa-preta)

**Critério:** cada entrada é dividida em classes que o sistema trata da mesma forma, e cada classe, válida ou inválida, recebe ao menos um caso de teste.

| Entrada | Classes válidas | Classes inválidas | Onde |
|---|---|---|---|
| Resultado do catálogo | `Found` | `Missing` (inexistente, inativo, excluído ou inconsistente), `Failed` (5xx, 4xx ≠ 404, timeout, corpo vazio ou malformado, exceção) | `CartServiceTest`, `CatalogHttpClientTest` |
| Estado da variante no carrinho | ainda não está; já está | removida por outra requisição durante a operação | `CartServiceTest` |
| `product_variant_id` | numérico, UUID, alfanumérico com hífen | vazio, nulo, em branco, `:` (separador de chave do Redis), `*`, espaço, `_`, `/`, não ASCII | `CartRequestsValidationTest`, `CartControllerTest` |
| Corpo HTTP | JSON válido | JSON malformado, vazio, campo ausente, tipo errado (`"quantity": "dois"`) | `CartControllerTest` |
| `sub` do JWT | UUID | texto que não é UUID (gera 401) | `CartControllerTest` |
| Status do item | `AVAILABLE`, `PRICE_CHANGED`, `UNVERIFIED` (compráveis) | `INSUFFICIENT_STOCK` (fora dos totais) | `CartItemStatusTest`, `CartMapperTest` |

**Seleção dos dados:** usamos um representante típico de cada classe. Para as classes inválidas do id, escolhemos caracteres que causariam dano real (`cart:*` como curinga de chave e `/` quebrando a URL), e não caracteres aleatórios.

### 3.2 Análise de valor-limite (caixa-preta)

**Critério:** para cada limite numérico, testar o valor exatamente no limite, logo abaixo e logo acima (limite - 1, limite, limite + 1). Os limites do serviço são configurados com valores pequenos nos testes (máximo de **10** unidades por item e **3** itens distintos), para que os dados fiquem legíveis. O valor padrão de produção (99/50) é só configuração e é coberto pela mesma regra.

| Regra | Valores testados |
|---|---|
| Máximo por item (10) | pedido 1, 10, 11; soma 9+1, 9+2; item já com 10 + 1 |
| Estoque (5) | pedido 5, 6; soma 3+2, 3+3; estoque 0 |
| Itens distintos (3) | 0, 2, 3, 4 itens no carrinho; 3 itens com a variante já presente (só soma) |
| Quantidade no POST | `Integer.MIN_VALUE`, -1, 0 (inválidos); 1, 99, `Integer.MAX_VALUE` (válidos na validação) |
| Quantidade no PATCH | `Integer.MIN_VALUE`, -1 (inválidos); 0 (remove), 1, 99 |
| Tamanho do id | 1 e 64 caracteres (válidos); 65 (inválido) |
| Preço do catálogo | 0,01 (menor válido); 0 e -0,01 (inválidos) |
| Estoque do catálogo | 0 (válido); -1 e ausente (inválidos) |
| Status: estoque × quantidade | estoque = quantidade (disponível); estoque = quantidade - 1 (insuficiente) |
| Mudança de preço | +0,01 e -0,01 (mudou); `10.00` × `10.0` (mesmo valor com escala diferente, não mudou) |
| Arredondamento (HALF_EVEN, 2 casas) | 0,125 → 0,12 e 0,135 → 0,14 (empates); 0,333 × 3 → 1,00; 19,99 × 99 |

### 3.3 Tabela de decisão (caixa-preta)

**Critério:** quando a saída depende da combinação de condições, testar todas as combinações.

Status de um item conferido no catálogo (`CartMapperTest.statusDecisionTable`):

| Estoque < quantidade | Preço mudou | Status |
|---|---|---|
| não | não | `AVAILABLE` |
| não | sim | `PRICE_CHANGED` |
| sim | não | `INSUFFICIENT_STOCK` |
| sim | sim | `INSUFFICIENT_STOCK` (estoque tem prioridade) |

Também testamos a precedência entre as regras do serviço: quando o limite por item e o estoque estouram juntos, a mensagem é a do limite por item, que é checado primeiro.

### 3.4 Transição de estados (caixa-preta)

**Critério:** cobrir todos os caminhos do laço de gravação otimista do `CartRepository.save` (`CartRepositoryTest`):

1. grava na 1ª tentativa, na ordem `WATCH → leitura → MULTI → HSET → EXEC`;
2. o `EXEC` é descartado (lista vazia ou `null`), a operação é refeita sobre o estado novo e grava na 3ª tentativa;
3. há conflito nas 10 tentativas → `RuleConflit` (409), com exatamente 10 tentativas;
4. a regra de negócio recusa a alteração → `UNWATCH`, nada é gravado e não há nova tentativa;
5. a thread é interrompida durante a espera entre tentativas → 409, e o sinal de interrupção é preservado.

### 3.5 Cobertura de comandos e decisões (caixa-branca)

**Critério:** 100% das linhas e todos os ramos alcançáveis do módulo, medidos pelo JaCoCo. Os ramos sem caso de teste depois da análise caixa-preta receberam casos próprios: lote do catálogo sem resposta para uma variante, construtor de produção com relógio do sistema, thread interrompida no lote e exceção inesperada no lote.

### 3.6 Teste de mutação

**Critério:** os testes devem detectar alterações artificiais no código (mutantes). Os mutantes que sobrevivem são analisados um a um: ou ganham um caso de teste, ou são justificados como equivalentes. O operador usado é o conjunto `STRONGER` do PIT.

## 4. Evidências de execução

### Como gerar

```bash
# Testes unitários do carrinho + relatório de cobertura (target/site/jacoco/index.html)
rm -f target/jacoco.exec
./mvnw test jacoco:report -Dtest='br.com.ateliedigital.cart.**.*Test,!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false

# Teste de mutação (target/pit-reports/index.html)
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

> O `rm -f target/jacoco.exec` é necessário porque o JaCoCo **acumula** execuções anteriores no mesmo arquivo. Sem ele, o relatório soma a cobertura de testes de integração rodados antes.

No CI, o job publica os relatórios `test-reports` (Surefire), `coverage-report` (JaCoCo) e `mutation-report` (PIT) como artefatos.

### Resultados (22/11/2026)

**Execução:** 169 testes unitários, 169 aprovados, 0 falhas, 0 erros (**100% de sucesso**).

| Classe | Testes |
|---|---|
| `CartServiceTest` | 43 |
| `CartRequestsValidationTest` | 30 |
| `CartControllerTest` | 29 |
| `CatalogHttpClientTest` | 22 |
| `CartMapperTest` | 20 |
| `CartRepositoryTest` | 12 |
| `CartItemStatusTest` | 4 |
| `CartConfigurationTest` | 3 |
| `ProductCatalogTest` | 3 |
| `CartItemTest` | 2 |
| `CartManagementTest` | 1 |
| **Total** | **169** |

**Cobertura JaCoCo (somente testes unitários, módulo `cart`):**

| Métrica | Cobertura |
|---|---|
| Linhas | 253/253 (**100%**) |
| Instruções | 1521/1523 (**99,9%**) |
| Ramos | 96/97 (**99,0%**) |

O único ramo não coberto é a resposta `null` do `RestClient.exchange` no `CatalogHttpClient`. Ele é inalcançável, porque o tratador da resposta nunca devolve `null`, e serve só como proteção.

**Teste de mutação (PIT):**

| Métrica | Valor |
|---|---|
| Mutantes gerados | 182 |
| Mortos (inclui 2 por *timeout*) | 175 |
| Sobreviventes | 7 |
| Sem cobertura | 0 |
| **Escore de mutação** | **96%** |

Os 7 sobreviventes são **equivalentes**, ou seja, não mudam nenhum comportamento observável:

- 5 no `CatalogHttpClient.inconsistency`: trocam a mensagem de inconsistência por `""`. A mensagem só aparece no log, e a variante continua sendo tratada como `Missing`.
- 1 no `CatalogHttpClient.lookup` (linha 51): remove o `if` que só escreve o *warning* de falha no log.
- 1 no `CatalogHttpClient.lookup` (linha 54): a proteção contra resposta `null` é o mesmo ramo inalcançável citado na cobertura.

Desconsiderando os equivalentes, o escore é 175/175 (100%).

## 5. Relatório de resultados parciais

### Problemas encontrados e correções

| Problema | Correção |
|---|---|
| A primeira rodada do PIT teve escore de 93% (164/176). Os mutantes revelaram três lacunas reais nos testes do `CatalogHttpClient`: corpo vazio, que era detectado só de forma indireta; lote interrompido; e exceção inesperada em uma consulta do lote. | Foram criados casos para os três. O de corpo vazio passou a conferir o motivo exato (`empty body`). O escore subiu para 96%. |
| O `CartConfiguration` (serialização do Redis) só era exercitado pelos testes de integração, e a cobertura unitária era 0%. | Criado o `CartConfigurationTest`: ida e volta do JSON e campos desconhecidos ignorados, sem Redis. |
| O relatório do JaCoCo mostrava cobertura falsa, somando execuções antigas. | Documentado o passo de apagar o `target/jacoco.exec` antes de medir. |
| `ModularityTests` falha no `./mvnw verify` por um ciclo `accounts → exceptions → store → accounts`. | O problema já existia antes desta tarefa e **não envolve o carrinho**. Está registrado para correção à parte. |

Nenhum defeito foi encontrado no código de produção do carrinho.

### Lições aprendidas

- Passar por uma linha não garante que ela seja verificada. O teste de corpo vazio já executava o ramo `payload == null`, mas a asserção `isInstanceOf(Failed)` também aceitava a falha por outro motivo. Só o PIT mostrou isso.
- Configurar limites pequenos nos testes (10/3 em vez de 99/50) deixa os valores-limite explícitos e os dados legíveis.
- Testes parametrizados com uma linha por valor-limite (`@CsvSource`) servem também como documentação da regra.
- Isolar o relógio (`Clock`) e o Redis (mock do `SessionCallback`) torna determinísticos os testes de datas e de concorrência otimista.
