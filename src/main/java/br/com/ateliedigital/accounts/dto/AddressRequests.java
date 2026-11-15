package br.com.ateliedigital.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddressRequests {
    public record AddressRequest(
            @NotBlank @Size(max = 8) String zipCode,
            @NotBlank String street,
            @NotBlank String number,
            String complement,
            @NotBlank String neighborhood,
            @NotBlank String city,
            @NotBlank @Size(min = 2, max = 2) String state,
            boolean main
    ) { }
}
