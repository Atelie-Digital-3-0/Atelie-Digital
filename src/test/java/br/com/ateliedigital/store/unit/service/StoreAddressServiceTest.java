package br.com.ateliedigital.store.unit.service;

import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.store.integration.viacep.ViaCepStoreClient;
import br.com.ateliedigital.store.integration.viacep.ViaCepStoreResponse;
import br.com.ateliedigital.store.integration.viacep.ZipCodeLookupException;
import br.com.ateliedigital.store.service.StoreAddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import br.com.ateliedigital.store.dto.responses.StoreAddressResponses.ZipCodeLookupResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StoreAddressServiceTest {
    @Mock
    private ViaCepStoreClient viaCepClient;

    private StoreAddressService addressService;

    @BeforeEach
    void setUp() {
        addressService = new StoreAddressService(viaCepClient);
    }

    @Test
    void shouldReturnFormattedZipCodeWhenZipCodeExists() {
        when(viaCepClient.find("59900000"))
                .thenReturn(new ViaCepStoreResponse(
                        "59900-000", "Rua Principal", "",
                        "Centro", "Pau dos Ferros", "RN", false
                ));

        String result = addressService.validateAndFormatZipCode("59900000");

        assertEquals("59900-000", result);
        verify(viaCepClient).find("59900000");
    }

    @Test
    void shouldNormalizeZipCodeBeforeLookup() {
        when(viaCepClient.find("59900000"))
                .thenReturn(new ViaCepStoreResponse(
                        "59900-000", "Rua Principal", "",
                        "Centro", "Pau dos Ferros", "RN", false
                ));

        String result = addressService.validateAndFormatZipCode("59900-000");

        assertEquals("59900-000", result);
        verify(viaCepClient).find("59900000");
    }

    @Test
    void shouldThrowExceptionWhenZipCodeDoesNotExist() {
        when(viaCepClient.find("00000000"))
                .thenReturn(new ViaCepStoreResponse(
                        null, null, null, null, null, null, true
                ));

        assertThrows(
                VioletedRule.class,
                () -> addressService.validateAndFormatZipCode("00000000")
        );
    }

    @Test
    void shouldThrowExceptionWhenZipCodeFormatIsInvalid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> addressService.validateAndFormatZipCode("12345")
        );

        verifyNoInteractions(viaCepClient);
    }

    @Test
    void shouldThrowExceptionWhenZipCodeIsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> addressService.validateAndFormatZipCode(null)
        );

        verifyNoInteractions(viaCepClient);
    }

    @Test
    void shouldAcceptFormattedZipCodeWhenViaCepIsUnavailable() {
        when(viaCepClient.find("59900000"))
                .thenThrow(new ZipCodeLookupException("Connection refused"));

        String result = addressService.validateAndFormatZipCode("59900000");

        assertEquals("59900-000", result);
    }

    @Test
    void shouldReturnAddressDetailsWhenZipCodeExists() {
        when(viaCepClient.find("59900000"))
                .thenReturn(new ViaCepStoreResponse(
                        "59900-000",
                        "Rua Principal",
                        "",
                        "Centro",
                        "Pau dos Ferros",
                        "RN",
                        false
                ));

        ZipCodeLookupResponse result =
                addressService.findAddressByZipCode("59900-000");

        assertAll(
                () -> assertEquals("59900-000", result.zipCode()),
                () -> assertEquals("Rua Principal", result.street()),
                () -> assertEquals("Centro", result.neighborhood()),
                () -> assertEquals("Pau dos Ferros", result.city()),
                () -> assertEquals("RN", result.state())
        );

        verify(viaCepClient).find("59900000");
    }

    @Test
    void shouldThrowExceptionWhenAddressLookupZipCodeDoesNotExist() {
        when(viaCepClient.find("00000000"))
                .thenReturn(new ViaCepStoreResponse(
                        null, null, null, null, null, null, true
                ));

        assertThrows(
                VioletedRule.class,
                () -> addressService.findAddressByZipCode("00000000")
        );
    }

    @Test
    void shouldPropagateExceptionWhenViaCepIsUnavailableDuringAddressLookup() {
        when(viaCepClient.find("59900000"))
                .thenThrow(new ZipCodeLookupException("Connection refused"));

        assertThrows(
                ZipCodeLookupException.class,
                () -> addressService.findAddressByZipCode("59900000")
        );
    }

    @Test
    void shouldThrowExceptionWhenAddressLookupZipCodeIsNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> addressService.findAddressByZipCode(null)
        );

        verifyNoInteractions(viaCepClient);
    }

    @Test
    void shouldThrowExceptionWhenAddressLookupZipCodeFormatIsInvalid() {
        assertThrows(
                IllegalArgumentException.class,
                () -> addressService.findAddressByZipCode("12345")
        );

        verifyNoInteractions(viaCepClient);
    }
}
