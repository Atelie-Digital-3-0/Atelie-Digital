package br.com.ateliedigital.store.service;

import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.store.integration.viacep.ViaCepStoreClient;
import br.com.ateliedigital.store.integration.viacep.ViaCepStoreResponse;
import br.com.ateliedigital.store.integration.viacep.ZipCodeLookupException;
import org.springframework.stereotype.Service;
import br.com.ateliedigital.store.dto.responses.StoreAddressResponses.ZipCodeLookupResponse;

@Service
public class StoreAddressService {
    private final ViaCepStoreClient viaCepClient;

    public StoreAddressService(ViaCepStoreClient viaCepClient) {
        this.viaCepClient = viaCepClient;
    }

    public String validateAndFormatZipCode(String rawZipCode) {
        if (rawZipCode == null) {
            throw new IllegalArgumentException("Zip code cannot be null");
        }

        String digits = rawZipCode.replaceAll("\\D", "");

        if (!digits.matches("\\d{8}")) {
            throw new IllegalArgumentException("Invalid zip code format");
        }

        try {
            ViaCepStoreResponse response = viaCepClient.find(digits);

            if (Boolean.TRUE.equals(response.erro())) {
                throw new VioletedRule("Zip code not found");
            }

        } catch (ZipCodeLookupException exception) {
            // ViaCEP indisponível: aceita o CEP após validar seu formato.
        }

        return digits.substring(0, 5) + "-" + digits.substring(5);
    }
    public ZipCodeLookupResponse findAddressByZipCode(String rawZipCode) {
        if (rawZipCode == null) {
            throw new IllegalArgumentException("Zip code cannot be null");
        }

        String digits = rawZipCode.replaceAll("\\D", "");

        if (!digits.matches("\\d{8}")) {
            throw new IllegalArgumentException("Invalid zip code format");
        }

        ViaCepStoreResponse response = viaCepClient.find(digits);

        if (Boolean.TRUE.equals(response.erro())) {
            throw new VioletedRule("Zip code not found");
        }

        return new ZipCodeLookupResponse(
                response.cep(),
                response.logradouro(),
                response.bairro(),
                response.localidade(),
                response.uf(),
                response.complemento()
        );
    }
}
