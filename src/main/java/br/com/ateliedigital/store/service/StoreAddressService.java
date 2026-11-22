package br.com.ateliedigital.store.service;

import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.store.dto.responses.StoreAddressResponses.ZipCodeLookupResponse;
import br.com.ateliedigital.store.integration.viacep.ViaCepStoreClient;
import br.com.ateliedigital.store.integration.viacep.ViaCepStoreResponse;
import br.com.ateliedigital.store.integration.viacep.ZipCodeLookupException;
import org.springframework.stereotype.Service;

@Service
public class StoreAddressService {
    private final ViaCepStoreClient viaCepClient;

    public StoreAddressService(ViaCepStoreClient viaCepClient) {
        this.viaCepClient = viaCepClient;
    }

    public String validateAndFormatZipCode(String rawZipCode) {
        String digits = normalizeAndValidateZipCode(rawZipCode);

        try {
            ViaCepStoreResponse response = viaCepClient.find(digits);

            if (Boolean.TRUE.equals(response.erro())) {
                throw new VioletedRule("Zip code not found");
            }

        } catch (ZipCodeLookupException exception) {
            // ViaCEP indisponível: aceita o CEP após validar seu formato.
        }

        return formatZipCode(digits);
    }

    public ZipCodeLookupResponse findAddressByZipCode(String rawZipCode) {
        String digits = normalizeAndValidateZipCode(rawZipCode);

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

    private String normalizeAndValidateZipCode(String rawZipCode) {
        if (rawZipCode == null) {
            throw new IllegalArgumentException("Zip code cannot be null");
        }

        String digits = rawZipCode.replaceAll("\\D", "");

        if (!digits.matches("\\d{8}")) {
            throw new IllegalArgumentException("Invalid zip code format");
        }

        return digits;
    }

    private String formatZipCode(String digits) {
        return digits.substring(0, 5) + "-" + digits.substring(5);
    }
}
