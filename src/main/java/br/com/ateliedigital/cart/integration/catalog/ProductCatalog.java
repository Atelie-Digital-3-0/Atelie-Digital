package br.com.ateliedigital.cart.integration.catalog;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Porta do modulo cart para o catalogo de produtos.
 * <p>
 * Hoje a implementacao e o {@link CatalogHttpClient} (atelie-catalog-api via HTTP). Quando o modulo catalog existir
 * no monolito, basta uma nova implementacao delegando para a API publica dele, sem tocar no restante do carrinho.
 * <p>
 * Implementacoes nunca lancam excecao por falha de comunicacao: devolvem {@link VariantLookup.Failed}.
 */
public interface ProductCatalog {

    VariantLookup lookup(String productVariantId);

    /** Consulta varias variantes; o mapa preserva a ordem (sem repeticao) dos ids recebidos. */
    default Map<String, VariantLookup> lookupAll(Collection<String> productVariantIds) {
        Map<String, VariantLookup> result = new LinkedHashMap<>();
        productVariantIds.forEach(id -> result.computeIfAbsent(id, this::lookup));
        return result;
    }
}
