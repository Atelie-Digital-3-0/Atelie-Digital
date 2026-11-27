package br.com.ateliedigital.cart.integration.catalog;

import java.math.BigDecimal;

/**
 * Dados de uma variante de produto ja validados pelo adaptador do catalogo: todos os campos obrigatorios estao
 * presentes, o preco e positivo e o estoque nao e negativo.
 */
public record ProductSnapshot(
        String productVariantId,
        String productId,
        String storeId,
        BigDecimal unitPrice,
        int stock,
        String sku,
        String color,
        String size
) { }
