package br.com.ateliedigital.cart.service;

import br.com.ateliedigital.cart.config.CartProperties;
import br.com.ateliedigital.cart.dto.CartRequests.AddCartItemRequest;
import br.com.ateliedigital.cart.dto.CartRequests.UpdateCartItemRequest;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemResponse;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemStatus;
import br.com.ateliedigital.cart.dto.CartResponses.CartResponse;
import br.com.ateliedigital.cart.dto.CartResponses.RemovalReason;
import br.com.ateliedigital.cart.integration.catalog.ProductCatalog;
import br.com.ateliedigital.cart.integration.catalog.ProductSnapshot;
import br.com.ateliedigital.cart.integration.catalog.VariantLookup;
import br.com.ateliedigital.cart.mapper.CartMapper;
import br.com.ateliedigital.cart.model.CartItem;
import br.com.ateliedigital.cart.repository.CartRepository;
import br.com.ateliedigital.cart.repository.CartRepository.ItemChange;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.VioletedRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes unitarios das regras do carrinho ({@link CartService}).
 * <p>
 * Repositorio (Redis) e catalogo sao mockados; o {@link CartMapper} e real porque e uma funcao pura. O relogio e
 * fixo para as datas serem deterministicas.
 * <p>
 * Tecnicas: <b>particao em classes de equivalencia</b> (Found/Missing/Failed do catalogo; item novo/existente),
 * <b>analise de valor-limite</b> (limite por item = 10, limite de itens distintos = 3, estoque) e
 * <b>cobertura de decisoes</b> (todos os ramos de {@code addItem}, {@code updateItem} e {@code getCart}).
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Instant ADDED = Instant.parse("2026-11-19T10:00:00Z");
    private static final UUID USER = UUID.fromString("5f1c3a52-8d2e-4a7b-9c41-0e6f2b7d9a10");

    /** Limites pequenos para os valores-limite caberem nos dados de teste. */
    private static final int MAX_QUANTITY = 10;
    private static final int MAX_DISTINCT = 3;

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductCatalog productCatalog;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, productCatalog, new CartMapper(),
                new CartProperties(MAX_QUANTITY, MAX_DISTINCT, "cart:"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // =====================================================================================================
    @Nested
    @DisplayName("addItem: adicionar item")
    class AddItem {

        @Test
        @DisplayName("Item novo e gravado com o snapshot do catalogo e added_at = updated_at = agora")
        void addsNewItemWithCatalogSnapshot() {
            foundInCatalog("7", "49.90", 5);
            simulateSave(null, 0);

            CartItemResponse response = service.addItem(USER, new AddCartItemRequest("7", 2));

            assertThat(response.quantity()).isEqualTo(2);
            assertThat(response.unitPrice()).isEqualByComparingTo("49.90");
            assertThat(response.subtotal()).isEqualByComparingTo("99.80");
            assertThat(response.status()).isEqualTo(CartItemStatus.AVAILABLE);
            assertThat(response.addedAt()).isEqualTo(NOW);
            assertThat(response.updatedAt()).isEqualTo(NOW);

            CartItem saved = savedItem(null, 0);
            assertThat(saved).isEqualTo(new CartItem("7", "product-7", "store-1", 2, new BigDecimal("49.90"),
                    "SKU-7", "azul", "M", NOW, NOW));
        }

        @Test
        @DisplayName("O item e gravado sob o id da variante informado pelo catalogo, no carrinho do usuario do token")
        void savesUnderTheUserAndVariantKey() {
            foundInCatalog("7", "49.90", 5);
            simulateSave(null, 0);

            service.addItem(USER, new AddCartItemRequest("7", 1));

            verify(cartRepository).save(eq(USER), eq("7"), any());
        }

        @Test
        @DisplayName("Item ja existente: soma a quantidade, mantem added_at e renova o preco do snapshot")
        void existingItemSumsQuantityAndRefreshesSnapshot() {
            foundInCatalog("7", "55.00", 10);
            simulateSave(item("7", 2, "49.90"), 1);

            CartItemResponse response = service.addItem(USER, new AddCartItemRequest("7", 3));

            assertThat(response.quantity()).isEqualTo(5);
            assertThat(response.unitPrice()).isEqualByComparingTo("55.00");
            assertThat(response.previousUnitPrice()).isNull();
            assertThat(response.status()).isEqualTo(CartItemStatus.AVAILABLE);
            assertThat(response.addedAt()).isEqualTo(ADDED);
            assertThat(response.updatedAt()).isEqualTo(NOW);
        }

        /** Valor-limite do maximo por item (10), com estoque folgado para isolar a regra. */
        @ParameterizedTest(name = "[{index}] no carrinho={0} + pedido={1} -> aceito={2}")
        @CsvSource({
                "0, 1,  true",    // minimo valido
                "0, 10, true",    // exatamente no limite
                "0, 11, false",   // limite + 1
                "9, 1,  true",    // soma exatamente no limite
                "9, 2,  false",   // soma no limite + 1
                "10, 1, false"    // item ja no limite
        })
        @DisplayName("Limite de quantidade por item (max = 10)")
        void quantityPerItemLimitBoundaries(int inCart, int requested, boolean accepted) {
            foundInCatalog("7", "10.00", 1_000);
            simulateSave(inCart == 0 ? null : item("7", inCart, "10.00"), inCart == 0 ? 0 : 1);

            if (accepted) {
                assertThat(service.addItem(USER, new AddCartItemRequest("7", requested)).quantity())
                        .isEqualTo(inCart + requested);
            } else {
                assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", requested)))
                        .isInstanceOf(VioletedRule.class)
                        .hasMessage("Quantity per item cannot exceed 10");
            }
        }

        /** Valor-limite do estoque (5), sempre abaixo do maximo por item para isolar a regra. */
        @ParameterizedTest(name = "[{index}] estoque=5, no carrinho={0} + pedido={1} -> aceito={2}")
        @CsvSource({
                "0, 5, true",     // pedido = estoque
                "0, 6, false",    // pedido = estoque + 1
                "3, 2, true",     // soma = estoque
                "3, 3, false"     // soma = estoque + 1
        })
        @DisplayName("Quantidade somada nao pode passar do estoque")
        void stockBoundaries(int inCart, int requested, boolean accepted) {
            foundInCatalog("7", "10.00", 5);
            simulateSave(inCart == 0 ? null : item("7", inCart, "10.00"), inCart == 0 ? 0 : 1);

            if (accepted) {
                assertThat(service.addItem(USER, new AddCartItemRequest("7", requested)).quantity())
                        .isEqualTo(inCart + requested);
            } else {
                assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", requested)))
                        .isInstanceOf(VioletedRule.class)
                        .hasMessage("Insufficient stock: only 5 units available");
            }
        }

        @Test
        @DisplayName("Estoque zerado recusa qualquer quantidade")
        void zeroStockRejectsAnyQuantity() {
            foundInCatalog("7", "10.00", 0);
            simulateSave(null, 0);

            assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 1)))
                    .isInstanceOf(VioletedRule.class)
                    .hasMessage("Insufficient stock: only 0 units available");
        }

        @Test
        @DisplayName("Quando os dois limites estouram, a mensagem e a do limite por item (checado primeiro)")
        void perItemLimitIsCheckedBeforeStock() {
            foundInCatalog("7", "10.00", 5);
            simulateSave(null, 0);

            assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 11)))
                    .isInstanceOf(VioletedRule.class)
                    .hasMessage("Quantity per item cannot exceed 10");
        }

        /** Valor-limite de itens distintos (3). O limite so vale para variantes que ainda nao estao no carrinho. */
        @ParameterizedTest(name = "[{index}] itens distintos={0}, variante ja no carrinho={1} -> aceito={2}")
        @CsvSource({
                "0, false, true",   // carrinho vazio
                "2, false, true",   // limite - 1: o novo item ocupa a ultima vaga
                "3, false, false",  // no limite: nao cabe item novo
                "4, false, false",  // acima do limite (ex.: limite reduzido na configuracao)
                "3, true,  true"    // no limite, mas a variante ja esta no carrinho: so soma
        })
        @DisplayName("Limite de itens distintos no carrinho (max = 3)")
        void distinctItemsLimitBoundaries(long distinctItems, boolean alreadyInCart, boolean accepted) {
            foundInCatalog("7", "10.00", 100);
            simulateSave(alreadyInCart ? item("7", 1, "10.00") : null, distinctItems);

            if (accepted) {
                assertThatCode(() -> service.addItem(USER, new AddCartItemRequest("7", 1))).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 1)))
                        .isInstanceOf(VioletedRule.class)
                        .hasMessage("Cart cannot have more than 3 different items");
            }
        }

        @Test
        @DisplayName("Produto inexistente/inativo no catalogo: 404 e nada e gravado")
        void missingProductIsRejected() {
            when(productCatalog.lookup("7")).thenReturn(VariantLookup.missing());

            assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 1)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product not found");
            verifyNoInteractions(cartRepository);
        }

        @Test
        @DisplayName("Catalogo indisponivel: 503 e nada e gravado")
        void catalogFailureIsServiceUnavailable() {
            when(productCatalog.lookup("7")).thenReturn(VariantLookup.failed("timeout"));

            assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 1)))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(exception.getReason()).isEqualTo("Catalog service unavailable");
                    });
            verifyNoInteractions(cartRepository);
        }

        @Test
        @DisplayName("O construtor de producao usa o relogio do sistema (UTC)")
        void productionConstructorUsesSystemClock() {
            CartService production = new CartService(cartRepository, productCatalog, new CartMapper(),
                    new CartProperties(MAX_QUANTITY, MAX_DISTINCT, "cart:"));
            foundInCatalog("7", "10.00", 5);
            simulateSave(null, 0);
            Instant before = Instant.now();

            CartItemResponse response = production.addItem(USER, new AddCartItemRequest("7", 1));

            assertThat(response.addedAt()).isBetween(before, Instant.now());
        }
    }

    // =====================================================================================================
    @Nested
    @DisplayName("getCart: consultar o carrinho")
    class GetCart {

        @Test
        @DisplayName("Carrinho vazio: totais zerados, verificado e sem consultar o catalogo")
        void emptyCart() {
            when(cartRepository.findAll(USER)).thenReturn(List.of());

            CartResponse cart = service.getCart(USER);

            assertThat(cart.items()).isEmpty();
            assertThat(cart.removedItems()).isEmpty();
            assertThat(cart.totalItems()).isZero();
            assertThat(cart.totalQuantity()).isZero();
            assertThat(cart.totalPrice()).isEqualByComparingTo("0");
            assertThat(cart.verified()).isTrue();
            verifyNoInteractions(productCatalog);
            verify(cartRepository, never()).removeAll(any(), anyCollection());
        }

        @Test
        @DisplayName("Reconciliacao: status por item, totais so dos compraveis e remocao dos indisponiveis")
        void reconcilesEveryLookupClass() {
            when(cartRepository.findAll(USER)).thenReturn(List.of(
                    item("1", 2, "10.00"),   // Found, preco igual          -> AVAILABLE
                    item("2", 1, "20.00"),   // Found, preco mudou          -> PRICE_CHANGED
                    item("3", 4, "5.00"),    // Found, estoque insuficiente -> INSUFFICIENT_STOCK
                    item("4", 1, "8.00"),    // Missing                     -> removido
                    item("5", 3, "1.50")));  // Failed                      -> UNVERIFIED
            Map<String, VariantLookup> lookups = new LinkedHashMap<>();
            lookups.put("1", VariantLookup.found(product("1", "10.00", 10)));
            lookups.put("2", VariantLookup.found(product("2", "25.00", 10)));
            lookups.put("3", VariantLookup.found(product("3", "5.00", 2)));
            lookups.put("4", VariantLookup.missing());
            lookups.put("5", VariantLookup.failed("timeout"));
            when(productCatalog.lookupAll(List.of("1", "2", "3", "4", "5"))).thenReturn(lookups);

            CartResponse cart = service.getCart(USER);

            assertThat(cart.items()).extracting(CartItemResponse::productVariantId, CartItemResponse::status)
                    .containsExactly(
                            tuple("1", CartItemStatus.AVAILABLE),
                            tuple("2", CartItemStatus.PRICE_CHANGED),
                            tuple("3", CartItemStatus.INSUFFICIENT_STOCK),
                            tuple("5", CartItemStatus.UNVERIFIED));
            CartItemResponse changed = cart.items().get(1);
            assertThat(changed.unitPrice()).isEqualByComparingTo("25.00");
            assertThat(changed.previousUnitPrice()).isEqualByComparingTo("20.00");
            // Totais: 1 (2 x 10,00) + 2 (1 x 25,00) + 5 (3 x 1,50, preco do snapshot); o item 3 fica de fora.
            assertThat(cart.totalQuantity()).isEqualTo(6);
            assertThat(cart.totalPrice()).isEqualByComparingTo("49.50");
            assertThat(cart.totalItems()).isEqualTo(4);
            assertThat(cart.verified()).isFalse();
            assertThat(cart.removedItems()).singleElement().satisfies(removed -> {
                assertThat(removed.productVariantId()).isEqualTo("4");
                assertThat(removed.reason()).isEqualTo(RemovalReason.PRODUCT_UNAVAILABLE);
            });
            verify(cartRepository).removeAll(USER, List.of("4"));
        }

        @Test
        @DisplayName("Todos os itens conferidos: verificado e nada e removido do Redis")
        void allFoundRemovesNothing() {
            when(cartRepository.findAll(USER)).thenReturn(List.of(item("1", 1, "10.00"), item("2", 2, "5.00")));
            when(productCatalog.lookupAll(List.of("1", "2"))).thenReturn(Map.of(
                    "1", VariantLookup.found(product("1", "10.00", 5)),
                    "2", VariantLookup.found(product("2", "5.00", 5))));

            CartResponse cart = service.getCart(USER);

            assertThat(cart.verified()).isTrue();
            assertThat(cart.removedItems()).isEmpty();
            assertThat(cart.totalPrice()).isEqualByComparingTo("20.00");
            verify(cartRepository, never()).removeAll(any(), anyCollection());
        }

        @Test
        @DisplayName("Todos os produtos sairam do catalogo: carrinho vazio e todos removidos de uma vez")
        void allMissingAreRemoved() {
            when(cartRepository.findAll(USER)).thenReturn(List.of(item("1", 1, "10.00"), item("2", 2, "5.00")));
            when(productCatalog.lookupAll(List.of("1", "2"))).thenReturn(Map.of(
                    "1", VariantLookup.missing(), "2", VariantLookup.missing()));

            CartResponse cart = service.getCart(USER);

            assertThat(cart.items()).isEmpty();
            assertThat(cart.totalPrice()).isEqualByComparingTo("0");
            assertThat(cart.removedItems()).extracting(r -> r.productVariantId()).containsExactly("1", "2");
            verify(cartRepository).removeAll(USER, List.of("1", "2"));
        }

        @Test
        @DisplayName("Variante sem resposta no lote do catalogo e tratada como nao verificada (nao e removida)")
        void variantMissingFromBatchIsUnverified() {
            when(cartRepository.findAll(USER)).thenReturn(List.of(item("1", 1, "10.00")));
            when(productCatalog.lookupAll(List.of("1"))).thenReturn(Map.of());

            CartResponse cart = service.getCart(USER);

            assertThat(cart.items()).singleElement()
                    .extracting(CartItemResponse::status).isEqualTo(CartItemStatus.UNVERIFIED);
            assertThat(cart.verified()).isFalse();
            verify(cartRepository, never()).removeAll(any(), anyCollection());
        }
    }

    // =====================================================================================================
    @Nested
    @DisplayName("updateItem: definir a quantidade final")
    class UpdateItem {

        @Test
        @DisplayName("Substitui a quantidade (nao soma), aceita o preco atual e mantem added_at")
        void replacesQuantityAndAcceptsCurrentPrice() {
            when(cartRepository.exists(USER, "7")).thenReturn(true);
            foundInCatalog("7", "59.90", 10);
            simulateSave(item("7", 4, "49.90"), 1);

            Optional<CartItemResponse> response = service.updateItem(USER, "7", new UpdateCartItemRequest(1));

            assertThat(response).get().satisfies(updated -> {
                assertThat(updated.quantity()).isEqualTo(1);
                assertThat(updated.unitPrice()).isEqualByComparingTo("59.90");
                assertThat(updated.status()).isEqualTo(CartItemStatus.AVAILABLE);
                assertThat(updated.previousUnitPrice()).isNull();
                assertThat(updated.addedAt()).isEqualTo(ADDED);
                assertThat(updated.updatedAt()).isEqualTo(NOW);
            });
            verify(cartRepository).save(eq(USER), eq("7"), any());
        }

        @Test
        @DisplayName("Quantidade 0 remove o item sem consultar o catalogo")
        void zeroRemovesTheItem() {
            when(cartRepository.remove(USER, "7")).thenReturn(true);

            Optional<CartItemResponse> response = service.updateItem(USER, "7", new UpdateCartItemRequest(0));

            assertThat(response).isEmpty();
            verify(cartRepository).remove(USER, "7");
            verifyNoInteractions(productCatalog);
        }

        @Test
        @DisplayName("Quantidade 0 para item que nao esta no carrinho: 404")
        void zeroForItemNotInCart() {
            when(cartRepository.remove(USER, "7")).thenReturn(false);

            assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(0)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product is not in the cart");
        }

        @Test
        @DisplayName("Item fora do carrinho: 404 sem consultar o catalogo")
        void itemNotInCart() {
            when(cartRepository.exists(USER, "7")).thenReturn(false);

            assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(2)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product is not in the cart");
            verifyNoInteractions(productCatalog);
            verify(cartRepository, never()).save(any(), anyString(), any());
        }

        @Test
        @DisplayName("Produto saiu do catalogo: o item e removido e responde 404")
        void missingProductIsRemoved() {
            when(cartRepository.exists(USER, "7")).thenReturn(true);
            when(productCatalog.lookup("7")).thenReturn(VariantLookup.missing());

            assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(2)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product is no longer available and was removed from the cart");
            verify(cartRepository).remove(USER, "7");
            verify(cartRepository, never()).save(any(), anyString(), any());
        }

        @Test
        @DisplayName("Catalogo indisponivel: 503 e o item nao e alterado nem removido")
        void catalogFailure() {
            when(cartRepository.exists(USER, "7")).thenReturn(true);
            when(productCatalog.lookup("7")).thenReturn(VariantLookup.failed("timeout"));

            assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(2)))
                    .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
            verify(cartRepository, never()).remove(any(), anyString());
            verify(cartRepository, never()).save(any(), anyString(), any());
        }

        @ParameterizedTest(name = "[{index}] estoque={0}, quantidade={1} -> aceito={2}")
        @CsvSource({
                "5,   5,  true",                 // quantidade = estoque
                "5,   6,  false",                // estoque + 1
                "100, 10, true",                 // quantidade = limite por item
                "100, 11, false"                 // limite por item + 1
        })
        @DisplayName("Valores-limite de estoque e do maximo por item na atualizacao")
        void updateBoundaries(int stock, int quantity, boolean accepted) {
            when(cartRepository.exists(USER, "7")).thenReturn(true);
            foundInCatalog("7", "10.00", stock);
            simulateSave(item("7", 1, "10.00"), 1);

            if (accepted) {
                assertThat(service.updateItem(USER, "7", new UpdateCartItemRequest(quantity)))
                        .get().extracting(CartItemResponse::quantity).isEqualTo(quantity);
            } else {
                assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(quantity)))
                        .isInstanceOf(VioletedRule.class);
            }
        }

        @Test
        @DisplayName("Atualizar item com o carrinho no limite de itens distintos e permitido (nao cria item novo)")
        void distinctLimitDoesNotApplyToUpdate() {
            when(cartRepository.exists(USER, "7")).thenReturn(true);
            foundInCatalog("7", "10.00", 10);
            simulateSave(item("7", 1, "10.00"), MAX_DISTINCT);

            assertThat(service.updateItem(USER, "7", new UpdateCartItemRequest(2))).isPresent();
        }

        @Test
        @DisplayName("Item removido por outra requisicao durante a consulta ao catalogo: 404")
        void itemRemovedConcurrently() {
            when(cartRepository.exists(USER, "7")).thenReturn(true);
            foundInCatalog("7", "10.00", 10);
            simulateSave(null, 0);

            assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(2)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product is not in the cart");
        }
    }

    // =====================================================================================================
    @Nested
    @DisplayName("removeItem e clear")
    class RemoveAndClear {

        @Test
        @DisplayName("Item existente e removido")
        void removesExistingItem() {
            when(cartRepository.remove(USER, "7")).thenReturn(true);

            assertThatCode(() -> service.removeItem(USER, "7")).doesNotThrowAnyException();
            verify(cartRepository).remove(USER, "7");
        }

        @Test
        @DisplayName("Remover item que nao esta no carrinho: 404")
        void removeItemNotInCart() {
            when(cartRepository.remove(USER, "7")).thenReturn(false);

            assertThatThrownBy(() -> service.removeItem(USER, "7"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product is not in the cart");
        }

        @Test
        @DisplayName("Limpar o carrinho apaga a chave do usuario sem consultar o catalogo")
        void clearCart() {
            service.clear(USER);

            verify(cartRepository).clear(USER);
            verifyNoInteractions(productCatalog);
        }
    }

    // ------------------------------------------------------------------------------------------- helpers

    private void foundInCatalog(String id, String price, int stock) {
        when(productCatalog.lookup(id)).thenReturn(VariantLookup.found(product(id, price, stock)));
    }

    /** Faz o mock do repositorio executar a alteracao sobre o estado informado, como o Redis faria. */
    private void simulateSave(CartItem current, long itemCount) {
        when(cartRepository.save(eq(USER), anyString(), any())).thenAnswer(invocation -> {
            ItemChange change = invocation.getArgument(2);
            return change.apply(current, itemCount);
        });
    }

    /** Reaplica a alteracao capturada sobre o estado informado para inspecionar o que seria gravado. */
    private CartItem savedItem(CartItem current, long itemCount) {
        ArgumentCaptor<ItemChange> change = ArgumentCaptor.forClass(ItemChange.class);
        verify(cartRepository).save(eq(USER), anyString(), change.capture());
        return change.getValue().apply(current, itemCount);
    }

    private static ProductSnapshot product(String id, String price, int stock) {
        return new ProductSnapshot(id, "product-" + id, "store-1", new BigDecimal(price), stock, "SKU-" + id,
                "azul", "M");
    }

    private static CartItem item(String id, int quantity, String price) {
        return new CartItem(id, "product-" + id, "store-1", quantity, new BigDecimal(price), "SKU-" + id,
                "azul", "M", ADDED, ADDED);
    }
}
