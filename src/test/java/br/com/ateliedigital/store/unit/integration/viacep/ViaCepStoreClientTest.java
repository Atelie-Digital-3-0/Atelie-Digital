package br.com.ateliedigital.store.unit.integration.viacep;


import br.com.ateliedigital.store.integration.viacep.ViaCepStoreClient;
import br.com.ateliedigital.store.integration.viacep.ViaCepStoreResponse;
import br.com.ateliedigital.store.integration.viacep.ZipCodeLookupException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


public class ViaCepStoreClientTest {
    private RestClient.Builder builder;
    private RestClient restClient;
    private RestClient.RequestHeadersUriSpec requestSpec;
    private RestClient.ResponseSpec responseSpec;

    private ViaCepStoreClient client;

    @BeforeEach
    void setUp() {
        builder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        requestSpec = mock(RestClient.RequestHeadersUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);
        when(restClient.get()).thenReturn(requestSpec);
        when(requestSpec.uri(anyString(), any(Object[].class)))
                .thenReturn(requestSpec);
        when(requestSpec.retrieve()).thenReturn(responseSpec);

        client = new ViaCepStoreClient(builder, "https://viacep.com.br");
    }

    @Test
    void shouldReturnAddressWhenZipCodeExists() {
        ViaCepStoreResponse expected = new ViaCepStoreResponse(
                "59900-000",
                "Rua Principal",
                "",
                "Centro",
                "Pau dos Ferros",
                "RN",
                false
        );

        when(responseSpec.body(ViaCepStoreResponse.class))
                .thenReturn(expected);

        ViaCepStoreResponse result = client.find("59900-000");

        assertNotNull(result);
        assertEquals("59900-000", result.cep());
        assertEquals("Rua Principal", result.logradouro());
        assertEquals("Centro", result.bairro());
        assertEquals("Pau dos Ferros", result.localidade());
        assertEquals("RN", result.uf());
        assertFalse(result.erro());
    }

    @Test
    void shouldReturnErrorWhenZipCodeDoesNotExist() {
        ViaCepStoreResponse expected = new ViaCepStoreResponse(
                null, null, null, null, null, null, true
        );

        when(responseSpec.body(ViaCepStoreResponse.class))
                .thenReturn(expected);

        ViaCepStoreResponse result = client.find("00000000");

        assertNotNull(result);
        assertTrue(result.erro());
    }

    @Test
    void shouldThrowExceptionWhenZipCodeFormatIsInvalid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.find("12345")
        );

        verifyNoInteractions(restClient);
    }

    @Test
    void shouldThrowExceptionWhenResponseIsEmpty() {
        when(responseSpec.body(ViaCepStoreResponse.class))
                .thenReturn(null);

        assertThrows(
                ZipCodeLookupException.class,
                () -> client.find("59900000")
        );
    }

    @Test
    void shouldThrowExceptionWhenCommunicationFails() {
        when(responseSpec.body(ViaCepStoreResponse.class))
                .thenThrow(new RuntimeException("Connection refused"));

        ZipCodeLookupException exception = assertThrows(
                ZipCodeLookupException.class,
                () -> client.find("59900000")
        );

        assertEquals(
                "Unable to retrieve zip code information",
                exception.getMessage()
        );

        assertNotNull(exception.getCause());
        assertEquals("Connection refused", exception.getCause().getMessage());
    }
    @Test
    void shouldThrowExceptionWhenZipCodeIsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> client.find(null)
        );

        verifyNoInteractions(restClient);
    }
}
