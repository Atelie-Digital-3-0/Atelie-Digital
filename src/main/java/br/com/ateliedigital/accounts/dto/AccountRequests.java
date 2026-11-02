package br.com.ateliedigital.accounts.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class AccountRequests {
    private AccountRequests() { }

    public record CreateAccountRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String cellphone,
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = "[0-9]{11}") String cpf,
            @NotBlank @Email String email
    ) { }

    public record UpdateAccountRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String cellphone,
            @NotNull @Past LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = "[0-9]{11}") String cpf,
            @NotBlank @Email String email
    ) { }

    public record AddressRequest(
            @NotBlank @Size(max = 8) String cep,
            String zipCode,
            @NotBlank String street,
            @NotBlank String number,
            String complement,
            @NotBlank String neighborhood,
            @NotBlank String city,
            @NotBlank @Size(min = 2, max = 2) String state,
            boolean main
    ) { }
}
