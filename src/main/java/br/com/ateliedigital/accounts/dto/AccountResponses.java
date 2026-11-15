package br.com.ateliedigital.accounts.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import br.com.ateliedigital.accounts.dto.AddressResponses.AddressResponse;

public final class AccountResponses {
    private AccountResponses() { }

    public record AccountResponse(UUID id, UUID keycloakId, String firstName, String lastName,
                                  String cellphone, LocalDate dateOfBirth, String cpf, String email, String bio,
                                  List<AddressResponse> addresses) { }
}
