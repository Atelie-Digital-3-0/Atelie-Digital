package br.com.ateliedigital.cart.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public final class CartRequests {
    /** Ids do catalogo: alfanumericos e hifen (numeros ou UUID), sem espacos nem separadores de chave. */
    public static final String VARIANT_ID_PATTERN = "[A-Za-z0-9-]{1,64}";

    private CartRequests() { }

    public record AddCartItemRequest(
            @NotBlank(message = "product_variant_id is required")
            @Pattern(regexp = VARIANT_ID_PATTERN, message = "product_variant_id is invalid")
            String productVariantId,
            @NotNull(message = "quantity is required")
            @Positive(message = "quantity must be greater than zero")
            Integer quantity
    ) { }

    /** Quantidade final desejada do item; {@code 0} remove o item do carrinho. */
    public record UpdateCartItemRequest(
            @NotNull(message = "quantity is required")
            @PositiveOrZero(message = "quantity must be zero or greater")
            Integer quantity
    ) { }
}
