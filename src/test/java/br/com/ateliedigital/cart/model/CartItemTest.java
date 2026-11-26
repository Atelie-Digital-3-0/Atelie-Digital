package br.com.ateliedigital.cart.model;

import br.com.ateliedigital.cart.integration.catalog.ProductSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Testes unitarios do snapshot gravado no Redis ({@link CartItem}). */
class CartItemTest {
    private static final Instant ADDED = Instant.parse("2026-11-19T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    @DisplayName("create copia os dados do catalogo e usa o mesmo instante para added_at e updated_at")
    void createCopiesCatalogData() {
        ProductSnapshot product = new ProductSnapshot("7", "70", "700", new BigDecimal("49.90"), 10, "SKU-7",
                "azul", "M");

        CartItem item = CartItem.create(product, 3, NOW);

        assertThat(item).isEqualTo(new CartItem("7", "70", "700", 3, new BigDecimal("49.90"), "SKU-7", "azul", "M",
                NOW, NOW));
    }

    @Test
    @DisplayName("refresh troca quantidade e snapshot, mas preserva o id da variante e o added_at original")
    void refreshKeepsIdentityAndAddedAt() {
        CartItem current = new CartItem("7", "70", "700", 1, new BigDecimal("49.90"), "SKU-7", "azul", "M",
                ADDED, ADDED);
        ProductSnapshot updated = new ProductSnapshot("other-id", "71", "701", new BigDecimal("59.90"), 10,
                "SKU-7B", "verde", "G");

        CartItem refreshed = current.refresh(updated, 4, NOW);

        assertThat(refreshed).isEqualTo(new CartItem("7", "71", "701", 4, new BigDecimal("59.90"), "SKU-7B",
                "verde", "G", ADDED, NOW));
    }
}
