package br.com.ateliedigital.cart.mapper;

import br.com.ateliedigital.cart.dto.CartResponses.CartItemResponse;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemStatus;
import br.com.ateliedigital.cart.dto.CartResponses.CartResponse;
import br.com.ateliedigital.cart.dto.CartResponses.RemovalReason;
import br.com.ateliedigital.cart.dto.CartResponses.RemovedCartItem;
import br.com.ateliedigital.cart.integration.catalog.ProductSnapshot;
import br.com.ateliedigital.cart.model.CartItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitarios do {@link CartMapper} (funcao pura, sem mocks).
 * <p>
 * Tecnicas: <b>tabela de decisao</b> para o status do item (condicoes: estoque &lt; quantidade? preco mudou?),
 * <b>valor-limite</b> no estoque (quantidade - 1, igual, + 1) e no arredondamento monetario (HALF_EVEN, 2 casas).
 */
class CartMapperTest {
    private static final Instant ADDED = Instant.parse("2026-11-19T10:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-11-20T12:00:00Z");

    private final CartMapper mapper = new CartMapper();

    @Nested
    @DisplayName("toResponse: item conferido no catalogo")
    class ToResponse {

        /**
         * Tabela de decisao do status. A regra de estoque tem prioridade sobre a de preco.
         * <pre>
         * | estoque < qtd | preco mudou | status             |
         * |      nao      |     nao     | AVAILABLE          |
         * |      nao      |     sim     | PRICE_CHANGED      |
         * |      sim      |     nao     | INSUFFICIENT_STOCK |
         * |      sim      |     sim     | INSUFFICIENT_STOCK |
         * </pre>
         */
        @ParameterizedTest(name = "[{index}] qtd={0}, estoque={1}, preco gravado={2}, atual={3} -> {4}")
        @CsvSource({
                "2, 2, 10.00, 10.00, AVAILABLE",           // estoque = quantidade (limite): ainda disponivel
                "2, 3, 10.00, 10.0,  AVAILABLE",           // escalas diferentes, mesmo valor: nao mudou
                "2, 2, 10.00, 10.01, PRICE_CHANGED",       // menor aumento possivel
                "2, 2, 10.00, 9.99,  PRICE_CHANGED",       // menor reducao possivel
                "3, 2, 10.00, 10.00, INSUFFICIENT_STOCK",  // estoque = quantidade - 1 (limite)
                "3, 2, 10.00, 12.00, INSUFFICIENT_STOCK",  // estoque tem prioridade sobre o preco
                "1, 0, 10.00, 10.00, INSUFFICIENT_STOCK"   // estoque zerado
        })
        @DisplayName("Tabela de decisao do status do item")
        void statusDecisionTable(int quantity, int stock, String savedPrice, String currentPrice,
                                 CartItemStatus expected) {
            CartItemResponse response = mapper.toResponse(item(quantity, savedPrice), product(currentPrice, stock));

            assertThat(response.status()).isEqualTo(expected);
        }

        @Test
        @DisplayName("Dados exibidos vem do catalogo; datas e quantidade vem do item gravado")
        void currentCatalogDataIsShown() {
            CartItem saved = new CartItem("7", "old-product", "old-store", 2, new BigDecimal("10.00"), "OLD-SKU",
                    "verde", "P", ADDED, UPDATED);
            ProductSnapshot current = new ProductSnapshot("7", "product-7", "store-1", new BigDecimal("10.00"), 8,
                    "SKU-7", "azul", "M");

            CartItemResponse response = mapper.toResponse(saved, current);

            assertThat(response).isEqualTo(new CartItemResponse("7", "product-7", "store-1", "SKU-7", "azul", "M", 2,
                    new BigDecimal("10.00"), null, new BigDecimal("20.00"), 8, CartItemStatus.AVAILABLE,
                    ADDED, UPDATED));
        }

        @Test
        @DisplayName("Preco mudou: previous_unit_price traz o preco gravado e o subtotal usa o preco atual")
        void priceChangedShowsPreviousPrice() {
            CartItemResponse response = mapper.toResponse(item(2, "49.90"), product("59.90", 10));

            assertThat(response.unitPrice()).isEqualByComparingTo("59.90");
            assertThat(response.previousUnitPrice()).isEqualByComparingTo("49.90");
            assertThat(response.subtotal()).isEqualByComparingTo("119.80");
        }

        @Test
        @DisplayName("Preco ausente no item gravado (registro antigo) conta como mudanca de preco")
        void nullSavedPriceIsAPriceChange() {
            CartItemResponse response = mapper.toResponse(item(1, null), product("10.00", 5));

            assertThat(response.status()).isEqualTo(CartItemStatus.PRICE_CHANGED);
            assertThat(response.previousUnitPrice()).isNull();
            assertThat(response.unitPrice()).isEqualByComparingTo("10.00");
        }

        @ParameterizedTest(name = "[{index}] {0} x {1} = {2}")
        @CsvSource({
                "0.333, 3, 1.00",    // 0,999 -> 1,00
                "0.125, 1, 0.12",    // HALF_EVEN: empate arredonda para o par (0,12)
                "0.135, 1, 0.14",    // HALF_EVEN: empate arredonda para o par (0,14)
                "19.99, 99, 1979.01" // maior quantidade permitida por padrao
        })
        @DisplayName("Valores monetarios tem 2 casas e arredondamento bancario (HALF_EVEN)")
        void moneyRounding(String price, int quantity, String expectedSubtotal) {
            CartItemResponse response = mapper.toResponse(item(quantity, price), product(price, 1_000));

            assertThat(response.subtotal()).isEqualTo(new BigDecimal(expectedSubtotal));
            assertThat(response.unitPrice().scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("toUnverifiedResponse: catalogo indisponivel")
    class ToUnverifiedResponse {

        @Test
        @DisplayName("Usa os dados do snapshot, sem estoque nem preco anterior, com status UNVERIFIED")
        void usesSnapshotData() {
            CartItem saved = new CartItem("7", "product-7", "store-1", 3, new BigDecimal("1.5"), "SKU-7", "azul",
                    "M", ADDED, UPDATED);

            CartItemResponse response = mapper.toUnverifiedResponse(saved);

            assertThat(response).isEqualTo(new CartItemResponse("7", "product-7", "store-1", "SKU-7", "azul", "M", 3,
                    new BigDecimal("1.50"), null, new BigDecimal("4.50"), null, CartItemStatus.UNVERIFIED,
                    ADDED, UPDATED));
        }

        @Test
        @DisplayName("Snapshot sem preco: preco nulo e subtotal zero")
        void nullPrice() {
            CartItemResponse response = mapper.toUnverifiedResponse(item(3, null));

            assertThat(response.unitPrice()).isNull();
            assertThat(response.subtotal()).isEqualTo(new BigDecimal("0.00"));
        }
    }

    @Nested
    @DisplayName("toCart: totais do carrinho")
    class ToCart {

        @Test
        @DisplayName("Lista vazia: totais zerados com 2 casas e carrinho verificado")
        void emptyCart() {
            CartResponse cart = mapper.toCart(List.of(), List.of());

            assertThat(cart.totalItems()).isZero();
            assertThat(cart.totalQuantity()).isZero();
            assertThat(cart.totalPrice()).isEqualTo(new BigDecimal("0.00"));
            assertThat(cart.verified()).isTrue();
            assertThat(cart.items()).isEmpty();
            assertThat(cart.removedItems()).isEmpty();
        }

        @Test
        @DisplayName("Itens sem estoque entram em total_items, mas ficam fora de total_quantity e total_price")
        void insufficientStockIsOutOfTotals() {
            List<CartItemResponse> items = List.of(
                    response(2, "20.00", CartItemStatus.AVAILABLE),
                    response(1, "25.00", CartItemStatus.PRICE_CHANGED),
                    response(4, "40.00", CartItemStatus.INSUFFICIENT_STOCK));
            List<RemovedCartItem> removed = List.of(new RemovedCartItem("9", RemovalReason.PRODUCT_UNAVAILABLE));

            CartResponse cart = mapper.toCart(items, removed);

            assertThat(cart.totalItems()).isEqualTo(3);
            assertThat(cart.totalQuantity()).isEqualTo(3);
            assertThat(cart.totalPrice()).isEqualTo(new BigDecimal("45.00"));
            assertThat(cart.verified()).isTrue();
            assertThat(cart.items()).isEqualTo(items);
            assertThat(cart.removedItems()).isEqualTo(removed);
        }

        @Test
        @DisplayName("Um unico item UNVERIFIED torna o carrinho nao verificado, mas conta nos totais")
        void unverifiedItemMarksCartAsNotVerified() {
            CartResponse cart = mapper.toCart(List.of(
                    response(1, "10.00", CartItemStatus.AVAILABLE),
                    response(2, "3.00", CartItemStatus.UNVERIFIED)), List.of());

            assertThat(cart.verified()).isFalse();
            assertThat(cart.totalQuantity()).isEqualTo(3);
            assertThat(cart.totalPrice()).isEqualTo(new BigDecimal("13.00"));
        }

        @Test
        @DisplayName("Somente itens sem estoque: totais zerados")
        void onlyInsufficientStock() {
            CartResponse cart = mapper.toCart(List.of(response(4, "40.00", CartItemStatus.INSUFFICIENT_STOCK)),
                    List.of());

            assertThat(cart.totalItems()).isEqualTo(1);
            assertThat(cart.totalQuantity()).isZero();
            assertThat(cart.totalPrice()).isEqualTo(new BigDecimal("0.00"));
        }
    }

    // ------------------------------------------------------------------------------------------- helpers

    private static CartItem item(int quantity, String price) {
        return new CartItem("7", "product-7", "store-1", quantity, price == null ? null : new BigDecimal(price),
                "SKU-7", "azul", "M", ADDED, UPDATED);
    }

    private static ProductSnapshot product(String price, int stock) {
        return new ProductSnapshot("7", "product-7", "store-1", new BigDecimal(price), stock, "SKU-7", "azul", "M");
    }

    private static CartItemResponse response(int quantity, String subtotal, CartItemStatus status) {
        return new CartItemResponse("7", "product-7", "store-1", "SKU-7", "azul", "M", quantity, null, null,
                new BigDecimal(subtotal), null, status, ADDED, UPDATED);
    }
}
