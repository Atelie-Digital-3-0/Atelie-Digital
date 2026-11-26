package br.com.ateliedigital.cart.integration.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Implementacao padrao de {@link ProductCatalog#lookupAll} (sequencial), usada por adaptadores simples. */
class ProductCatalogTest {

    @Test
    @DisplayName("lookupAll preserva a ordem, remove repetidos e consulta cada id uma unica vez")
    void lookupAllKeepsOrderAndDeduplicates() {
        List<String> consulted = new ArrayList<>();
        ProductCatalog catalog = id -> {
            consulted.add(id);
            return "2".equals(id) ? VariantLookup.missing() : VariantLookup.found(
                    new ProductSnapshot(id, "p", "s", BigDecimal.ONE, 1, null, null, null));
        };

        Map<String, VariantLookup> result = catalog.lookupAll(List.of("3", "1", "2", "3", "1"));

        assertThat(result.keySet()).containsExactly("3", "1", "2");
        assertThat(result.get("2")).isInstanceOf(VariantLookup.Missing.class);
        assertThat(result.get("1")).isInstanceOf(VariantLookup.Found.class);
        assertThat(consulted).containsExactly("3", "1", "2");
    }

    @Test
    @DisplayName("lookupAll de uma lista vazia nao consulta o catalogo")
    void lookupAllEmpty() {
        List<String> consulted = new ArrayList<>();
        ProductCatalog catalog = id -> {
            consulted.add(id);
            return VariantLookup.missing();
        };

        assertThat(catalog.lookupAll(List.of())).isEmpty();
        assertThat(consulted).isEmpty();
    }

    @Test
    @DisplayName("As fabricas de VariantLookup criam cada classe do resultado")
    void variantLookupFactories() {
        ProductSnapshot product = new ProductSnapshot("1", "p", "s", BigDecimal.ONE, 1, null, null, null);

        assertThat(VariantLookup.found(product)).isEqualTo(new VariantLookup.Found(product));
        assertThat(VariantLookup.missing()).isEqualTo(new VariantLookup.Missing());
        assertThat(VariantLookup.failed("timeout")).isEqualTo(new VariantLookup.Failed("timeout"));
    }
}
