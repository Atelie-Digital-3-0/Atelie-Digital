package br.com.ateliedigital.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CartResponses {
    private CartResponses() { }

    /**
     * Situacao de um item frente ao catalogo no momento da consulta.
     * <ul>
     *     <li>{@code AVAILABLE}: pode ser comprado pelo preco exibido;</li>
     *     <li>{@code PRICE_CHANGED}: pode ser comprado, mas o preco mudou desde que foi adicionado
     *     ({@code previous_unit_price} traz o preco antigo);</li>
     *     <li>{@code INSUFFICIENT_STOCK}: o estoque atual e menor que a quantidade no carrinho (fora dos totais);</li>
     *     <li>{@code UNVERIFIED}: o catalogo nao respondeu; exibido com o preco do snapshot.</li>
     * </ul>
     */
    public enum CartItemStatus {
        AVAILABLE, PRICE_CHANGED, INSUFFICIENT_STOCK, UNVERIFIED;

        public boolean purchasable() {
            return this != INSUFFICIENT_STOCK;
        }
    }

    public enum RemovalReason { PRODUCT_UNAVAILABLE }

    public record CartItemResponse(
            String productVariantId,
            String productId,
            String storeId,
            String sku,
            String color,
            String size,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal previousUnitPrice,
            BigDecimal subtotal,
            Integer availableStock,
            CartItemStatus status,
            Instant addedAt,
            Instant updatedAt
    ) { }

    /** Item retirado do carrinho durante a consulta porque o produto deixou de existir/estar ativo. */
    public record RemovedCartItem(String productVariantId, RemovalReason reason) { }

    /**
     * @param totalQuantity soma das quantidades dos itens compraveis
     * @param totalPrice    soma dos subtotais dos itens compraveis
     * @param verified      {@code false} se algum item nao pode ser conferido no catalogo
     */
    public record CartResponse(
            List<CartItemResponse> items,
            int totalItems,
            int totalQuantity,
            BigDecimal totalPrice,
            boolean verified,
            List<RemovedCartItem> removedItems
    ) { }
}
