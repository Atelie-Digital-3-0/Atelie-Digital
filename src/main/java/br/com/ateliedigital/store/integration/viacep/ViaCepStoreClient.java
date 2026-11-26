package br.com.ateliedigital.store.integration.viacep;

import br.com.ateliedigital.exceptions.ZipCodeLookupException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ViaCepStoreClient {

    private static final Logger log =
            LoggerFactory.getLogger(ViaCepStoreClient.class);

    private final RestClient restClient;

    public ViaCepStoreClient(
            RestClient.Builder builder,
            @Value("${atelie.viacep.base-url:https://viacep.com.br}")
            String baseUrl
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public ViaCepStoreResponse find(String rawZipCode) {
        if (rawZipCode == null) {
            throw new IllegalArgumentException("Zip code cannot be null");
        }

        String zipCode = rawZipCode.replaceAll("\\D", "");

        if (!zipCode.matches("\\d{8}")) {
            throw new IllegalArgumentException("Invalid zip code format");
        }

        try {
            ViaCepStoreResponse response = restClient.get()
                    .uri("/ws/{zipCode}/json/", zipCode)
                    .retrieve()
                    .body(ViaCepStoreResponse.class);

            if (response == null) {
                throw new ZipCodeLookupException(
                        "Empty response received from ViaCEP"
                );
            }

            return response;

        } catch (ZipCodeLookupException | IllegalArgumentException exception) {
            throw exception;

        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to retrieve zip code {} from ViaCEP",
                    zipCode,
                    exception
            );

            throw new ZipCodeLookupException(
                    "Unable to retrieve zip code information",
                    exception
            );
        }
    }
}
