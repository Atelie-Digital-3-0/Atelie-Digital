package br.com.ateliedigital.cart.model;

import br.com.ateliedigital.cart.integration.catalog.ProductSnapshot;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item do carrinho como fica gravado no Redis (serializado em JSON dentro do hash {@code cart:{keycloakId}},
 * um campo por variante de produto).
 * <p>
 * Guarda um <b>snapshot</b> dos dados do produto no momento em que foi adicionado/atualizado. Na leitura do carrinho
 * o snapshot e comparado com o catalogo para sinalizar mudanca de preco ou falta de estoque.
 *
 * @param productVariantId id da variante no catalogo (tambem e o campo do hash)
 * @param unitPrice        preco unitario no momento da ultima adicao/atualizacao
 */
public record CartItem(
        String productVariantId,
        String productId,
        String storeId,
        int quantity,
        BigDecimal unitPrice,
        String sku,
        String color,
        String size,
        Instant addedAt,
        Instant updatedAt
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Novo item a partir dos dados atuais do catalogo. */
    public static CartItem create(ProductSnapshot product, int quantity, Instant now) {
        return new CartItem(product.productVariantId(), product.productId(), product.storeId(), quantity,
                product.unitPrice(), product.sku(), product.color(), product.size(), now, now);
    }

    /** Mesmo item com nova quantidade e snapshot renovado (o usuario passa a "aceitar" o preco atual). */
    public CartItem refresh(ProductSnapshot product, int newQuantity, Instant now) {
        return new CartItem(productVariantId, product.productId(), product.storeId(), newQuantity,
                product.unitPrice(), product.sku(), product.color(), product.size(), addedAt, now);
    }
}
