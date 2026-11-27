package br.com.ateliedigital.cart.integration.catalog;

/**
 * Resultado da consulta de uma variante no catalogo. Distingue "o produto nao pode ser vendido" de
 * "nao foi possivel consultar", que exigem tratamentos diferentes no carrinho.
 */
public sealed interface VariantLookup {

    /** Variante existe, esta ativa e os dados sao consistentes. */
    record Found(ProductSnapshot product) implements VariantLookup { }

    /** Variante inexistente, excluida, desativada ou com dados inconsistentes: nao pode ficar no carrinho. */
    record Missing() implements VariantLookup { }

    /** Catalogo indisponivel ou respondeu erro: o estado do produto e desconhecido. */
    record Failed(String reason) implements VariantLookup { }

    static VariantLookup found(ProductSnapshot product) {
        return new Found(product);
    }

    static VariantLookup missing() {
        return new Missing();
    }

    static VariantLookup failed(String reason) {
        return new Failed(reason);
    }
}
