package br.com.ateliedigital.accounts.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AccountResponses {
    private AccountResponses() { }

    public record AccountResponse(UUID id, UUID keycloakId, String firstName, String lastName,
                                  String cellphone, LocalDate dateOfBirth, String cpf, String email,
                                  List<AddressResponse> addresses) { }

    public record AddressResponse(UUID id, String cep, String zipCode, String street, String number,
                                  String complement, String neighborhood, String city, String state,
                                  boolean main) { }

    public record CepResponse(String cep, String street, String complement, String neighborhood,
                              String city, String state, boolean found) { }
}
