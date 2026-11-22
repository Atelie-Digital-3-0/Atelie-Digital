package br.com.ateliedigital.cart.dto;

import br.com.ateliedigital.cart.dto.CartResponses.CartItemStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

/** Quais status contam nos totais do carrinho. Uma linha por valor do enum (cobertura total do dominio). */
class CartItemStatusTest {

    @ParameterizedTest(name = "[{index}] {0} -> compravel={1}")
    @CsvSource({
            "AVAILABLE,          true",
            "PRICE_CHANGED,      true",
            "INSUFFICIENT_STOCK, false",
            "UNVERIFIED,         true"
    })
    @DisplayName("Somente INSUFFICIENT_STOCK fica fora dos totais")
    void purchasable(CartItemStatus status, boolean expected) {
        assertThat(status.purchasable()).isEqualTo(expected);
    }
}
