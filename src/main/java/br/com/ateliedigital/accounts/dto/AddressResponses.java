package br.com.ateliedigital.accounts.dto;

import java.util.UUID;

public class AddressResponses {
    public record AddressResponse(UUID id, String zipCode, String street, String number,
                                  String complement, String neighborhood, String city, String state,
                                  boolean main) { }

    public record ZipCodeResponse(String zipCode, String street, String complement, String neighborhood,
                              String city, String state, boolean found) { }
}
