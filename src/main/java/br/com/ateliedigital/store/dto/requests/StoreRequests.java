package br.com.ateliedigital.store.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;



public class StoreRequests {

    public record CreateStoreRequest(
            @NotBlank(message = "O nome da loja é obrigatório")
            @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres")
            String name,

            @NotBlank(message = "O telefone da loja é obrigatório")
            @Pattern(
                    regexp = "^(?:\\+55\\s?)?\\(?[1-9][0-9]\\)?\\s?9?[0-9]{4}-?[0-9]{4}$",
                    message = "Formato de telefone inválido"
            )
            String phone,

            @NotBlank(message = "O e-mail da loja é obrigatório")
            @Email(message = "Formato de e-mail inválido")
            @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
            String email,

            @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
            String description,

            @NotNull(message = "O ID da categoria é obrigatório")
            UUID categoryId,

            @Valid
            @NotNull(message = "O endereço é obrigatório")
            StoreAddressRequests.AddressRequest address
    ) {}

    public record UpdateStoreRequest(
            @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
            String description,

            @Pattern(
                    regexp = "^(?:\\+55\\s?)?\\(?[1-9][0-9]\\)?\\s?9?[0-9]{4}-?[0-9]{4}$",
                    message = "Formato de telefone inválido"
            )
            String phone,

            @Email(message = "Formato de e-mail inválido")
            @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
            @Pattern(
                    regexp = ".*\\S.*",
                    message = "O e-mail não pode estar em branco"
            )
            String email,

            @Valid
            StoreAddressRequests.AddressUpdateRequest address
    ) {}

    public record FilterStoreRequest(
            @Min(0) int offset,
            @Min(1) int limit,
            String name,
            UUID categoryId
    ) {}
}
