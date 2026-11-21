package br.com.ateliedigital.store.unit.controller;


import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.store.controller.StoreAddressController;
import br.com.ateliedigital.store.dto.responses.StoreAddressResponses.ZipCodeLookupResponse;
import br.com.ateliedigital.store.integration.viacep.ZipCodeLookupException;
import br.com.ateliedigital.store.service.StoreAddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.ateliedigital.exceptions.GlobalExceptionHandler;

public class StoreAddressControllerTest {
    private StoreAddressService addressService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        addressService = mock(StoreAddressService.class);

        StoreAddressController controller =
                new StoreAddressController(addressService);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnAddressWhenZipCodeExists() throws Exception {
        ZipCodeLookupResponse response = new ZipCodeLookupResponse(
                "59900-000",
                "Rua Principal",
                "Centro",
                "Pau dos Ferros",
                "RN",
                ""
        );

        when(addressService.findAddressByZipCode("59900-000"))
                .thenReturn(response);

        mockMvc.perform(get("/api/stores/addresses/zip-code/59900-000"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.zipCode").value("59900-000"))
                .andExpect(jsonPath("$.street").value("Rua Principal"))
                .andExpect(jsonPath("$.neighborhood").value("Centro"))
                .andExpect(jsonPath("$.city").value("Pau dos Ferros"))
                .andExpect(jsonPath("$.state").value("RN"));

        verify(addressService).findAddressByZipCode("59900-000");
    }

    @Test
    void shouldReturnErrorWhenZipCodeDoesNotExist() throws Exception {
        when(addressService.findAddressByZipCode("00000000"))
                .thenThrow(new VioletedRule("Zip code not found"));

        mockMvc.perform(get("/api/stores/addresses/zip-code/00000000"))
                .andExpect(status().isConflict());

        verify(addressService).findAddressByZipCode("00000000");
    }

    @Test
    void shouldReturnErrorWhenZipCodeFormatIsInvalid() throws Exception {
        when(addressService.findAddressByZipCode("12345"))
                .thenThrow(new IllegalArgumentException("Invalid zip code format"));

        mockMvc.perform(get("/api/stores/addresses/zip-code/12345"))
                .andExpect(status().isBadRequest());

        verify(addressService).findAddressByZipCode("12345");
    }

    @Test
    void shouldReturnErrorWhenViaCepIsUnavailable() throws Exception {
        when(addressService.findAddressByZipCode("59900000"))
                .thenThrow(new ZipCodeLookupException("Connection refused"));

        mockMvc.perform(get("/api/stores/addresses/zip-code/59900000"))
                .andExpect(status().isBadGateway());

        verify(addressService).findAddressByZipCode("59900000");
    }
}
