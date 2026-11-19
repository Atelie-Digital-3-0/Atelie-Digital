package br.com.ateliedigital.store.dto.requests;

import jakarta.validation.constraints.*;

public class StoreAddressRequests {

    public record AddressRequest(
            @NotBlank(message = "A rua é obrigatória")
            @Size(max = 255)
            String street,

            @NotBlank(message = "O número é obrigatório")
            @Size(max = 20)
            String number,

            @NotBlank(message = "O bairro é obrigatório")
            @Size(max = 255)
            String neighborhood,

            @NotBlank(message = "A cidade é obrigatória")
            @Size(max = 100)
            String city,

            @NotBlank(message = "O estado é obrigatório")
            @Size(min = 2, max = 2, message = "O estado deve ter exatamente 2 caracteres (ex: RN)")
            String state,

            @NotBlank(message = "O CEP é obrigatório")
            @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP em formato inválido. Use 00000-000")
            String zipCode,

            @Size(max = 255)
            String complement
    ) {}

    public record AddressUpdateRequest(
            @Size(max = 255) String street,
            @Size(max = 20) String number,
            @Size(max = 255) String neighborhood,
            @Size(max = 100) String city,
            @Size(min = 2, max = 2) String state,
            @Pattern(regexp = "^\\d{5}-?\\d{3}$") String zipCode,
            @Size(max = 255) String complement
    ) {}

}
