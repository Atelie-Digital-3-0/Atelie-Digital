package br.com.ateliedigital.accounts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.validation.ValidCpf;

import java.time.LocalDate;

public final class AccountRequests {
    private static final String NOT_BLANK = ".*\\S.*";

    private AccountRequests() { }

    public record CreateAccountRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String cellphone,
            @NotNull(message = "date_of_birth is required") @Past(message = "date_of_birth must be a past date") LocalDate dateOfBirth,
            @ValidCpf String cpf,
            @NotBlank @Email String email
    ) { }

    /**
     * Payload do {@code PATCH /me}: todos os campos sao opcionais. Ausente (ou {@code null}) = nao altera;
     * quando presente, o valor precisa ser valido (nao pode ser em branco).
     */
    public record UpdateAccountRequest(
            @Pattern(regexp = NOT_BLANK, message = "first_name must not be blank") String firstName,
            @Pattern(regexp = NOT_BLANK, message = "last_name must not be blank") String lastName,
            @Pattern(regexp = NOT_BLANK, message = "cellphone must not be blank") String cellphone,
            @Past(message = "date_of_birth must be a past date") LocalDate dateOfBirth,
            @Email(message = "email must be a valid e-mail") String email,
            String bio
    ) { }

    public record CompleteAccountRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank String cellphone,
            @NotNull(message = "date_of_birth is required") @Past(message = "date_of_birth must be a past date") LocalDate dateOfBirth,
            @ValidCpf String cpf,
            String bio,
            @NotNull(message = "Campo obrigatório") @Valid AddressRequest address
    ){}

}
