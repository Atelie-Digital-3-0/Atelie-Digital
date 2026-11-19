package br.com.ateliedigital.store.dto.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;



public class StoreRequests {

    public record CreateStoreRequest(
            @NotBlank(message = "O nome da loja é obrigatório")
            @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres")
            String name,

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
