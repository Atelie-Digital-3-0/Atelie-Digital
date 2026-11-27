package br.com.ateliedigital.product.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.math.BigDecimal;

public class ProductRequests {

    public record CreateProductRequest(

            @NotBlank(message = "O nome do produto é obrigatório")
            @Size(min = 3, max = 150,
                    message = "O nome deve ter entre 3 e 150 caracteres")
            String name,

            @Size(max = 5000,
                    message = "A descrição deve ter no máximo 5000 caracteres")
            String description,

            @NotEmpty(message = "O produto deve possuir pelo menos uma variação")
            List<@NotNull @Valid ProductVariationRequest> variations

    ) {}

    public record UpdateProductRequest(

            @Size(min = 3, max = 150,
                    message = "O nome deve ter entre 3 e 150 caracteres")
            @Pattern(regexp = ".*\\S.*",
                    message = "O nome não pode estar em branco")
            String name,

            @Size(max = 5000,
                    message = "A descrição deve ter no máximo 5000 caracteres")
            String description

    ) {}

    public record ProductVariationRequest(

            @NotNull(message = "O preço é obrigatório")
            @DecimalMin(value = "0.00",
                    message = "O preço não pode ser negativo")
            @Digits(integer = 10, fraction = 2,
                    message = "O preço deve possuir até 10 dígitos inteiros e 2 decimais")
            BigDecimal price,

            @DecimalMin(value = "0.0", inclusive = false,
                    message = "O peso deve ser maior que zero")
            @Digits(integer = 7, fraction = 3)
            BigDecimal weight,

            @DecimalMin(value = "0.0", inclusive = false,
                    message = "O comprimento deve ser maior que zero")
            @Digits(integer = 7, fraction = 3)
            BigDecimal length,

            @DecimalMin(value = "0.0", inclusive = false,
                    message = "A largura deve ser maior que zero")
            @Digits(integer = 7, fraction = 3)
            BigDecimal width,

            @DecimalMin(value = "0.0", inclusive = false,
                    message = "A altura deve ser maior que zero")
            @Digits(integer = 7, fraction = 3)
            BigDecimal height,

            @Size(max = 50, message = "O SKU deve ter no máximo 50 caracteres")
            String sku,

            @NotNull(message = "O estoque é obrigatório")
            @Min(value = 0, message = "O estoque não pode ser negativo")
            Integer stock,

            @Size(max = 100)
            String color,

            @Size(max = 100)
            String size,

            @NotNull(message = "Informe se a variação é a principal")
            Boolean primary

    ) {}

    public record UpdateProductVariationRequest(

            @DecimalMin(value = "0.00",
                    message = "O preço não pode ser negativo")
            @Digits(integer = 10, fraction = 2)
            BigDecimal price,

            @DecimalMin(value = "0.0", inclusive = false)
            @Digits(integer = 7, fraction = 3)
            BigDecimal weight,

            @DecimalMin(value = "0.0", inclusive = false)
            @Digits(integer = 7, fraction = 3)
            BigDecimal length,

            @DecimalMin(value = "0.0", inclusive = false)
            @Digits(integer = 7, fraction = 3)
            BigDecimal width,

            @DecimalMin(value = "0.0", inclusive = false)
            @Digits(integer = 7, fraction = 3)
            BigDecimal height,

            @Size(max = 50)
            String sku,

            @Min(value = 0, message = "O estoque não pode ser negativo")
            Integer stock,

            @Size(max = 100)
            String color,

            @Size(max = 100)
            String size,

            Boolean primary

    ) {}
}
