package br.com.ateliedigital.accounts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;

import java.time.LocalDate;

public final class AccountRequests {
    private AccountRequests() { }

    public record CreateAccountRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String cellphone,
            @NotNull(message = "date_of_birth is required") @Past(message = "date_of_birth must be a past date") LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = "[0-9]{11}") String cpf,
            @NotBlank @Email String email
    ) { }

    public record UpdateAccountRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String cellphone,
            @NotNull(message = "date_of_birth is required") @Past(message = "date_of_birth must be a past date") LocalDate dateOfBirth,
            @NotBlank @Email String email,
            String bio
    ) { }

    public record CompleteAccountRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String cellphone,
            @NotNull(message = "date_of_birth is required") @Past(message = "date_of_birth must be a past date") LocalDate dateOfBirth,
            @NotBlank @Pattern(regexp = "[0-9]{11}") String cpf,
            String bio,
            @NotNull(message = "Campo obrigatório") @Valid AddressRequest address
    ){}

}
