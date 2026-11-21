package br.com.ateliedigital.store.dto.responses;

import java.util.UUID;

public class StoreAddressResponses {

    public record AddressResponse(
            UUID id,
            String street,
            String number,
            String neighborhood,
            String city,
            String state,
            String zipCode,
            String complement
    ) {}

    public record ZipCodeLookupResponse(
            String zipCode,
            String street,
            String neighborhood,
            String city,
            String state,
            String complement
    ) {}
}
