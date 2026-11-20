package br.com.ateliedigital.accounts.controller;

import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AddressResponses.AddressResponse;
import br.com.ateliedigital.accounts.dto.AddressResponses.ZipCodeResponse;
import br.com.ateliedigital.accounts.integration.viacep.ViaCepClient;
import br.com.ateliedigital.accounts.service.AddressService;
import br.com.ateliedigital.exceptions.GlobalExceptionHandler;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

    private static final String BASE_URL = "/api/accounts/me/addresses";

    @Mock
    private AddressService addressService;
    @Mock
    private ViaCepClient viaCepClient;

    @InjectMocks
    private AddressController controller;

    private MockMvc mockMvc;
    private JwtAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .build();

        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", UUID.randomUUID().toString()));
        authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    @AfterEach
    void tearDown() {
        // Os mocks sao recriados a cada teste pelo MockitoExtension; nada a limpar manualmente.
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("Address created with success returns 201 and Location")
    void create() throws Exception {
        AddressResponse response = response(UUID.randomUUID());
        when(addressService.create(authentication, addressRequest())).thenReturn(response);

        mockMvc.perform(post(BASE_URL)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Rua A", "SP")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost" + BASE_URL + "/" + response.id()))
                .andExpect(jsonPath("$.id").value(response.id().toString()))
                .andExpect(jsonPath("$.zip_code").value("13000000"))
                .andExpect(jsonPath("$.main").value(true));
    }

    @Test
    @DisplayName("Address not created because street is blank")
    void createFailsWhenStreetIsBlank() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson(" ", "SP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(addressService);
    }

    @Test
    @DisplayName("Address not created because state is invalid")
    void createFailsWhenStateIsInvalid() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Rua A", "SAO")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(addressService);
    }

    @Test
    @DisplayName("Address not created because address is registered")
    void createFailsWhenAddressIsAlreadyRegistered() throws Exception {
        when(addressService.create(any(), any())).thenThrow(new RuleConflit("Address is already registered"));

        mockMvc.perform(post(BASE_URL)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Rua A", "SP")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Address is already registered"));
    }

    // ------------------------------------------------------------------ list

    @Test
    @DisplayName("Addresses listed with success using pagination")
    void list() throws Exception {
        AddressResponse response = response(UUID.randomUUID());
        PageRequest pageable = PageRequest.of(1, 5);
        when(addressService.list(authentication, pageable))
                .thenReturn(new PageImpl<>(List.of(response), pageable, 6));

        mockMvc.perform(get(BASE_URL).principal(authentication).param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(response.id().toString()))
                .andExpect(jsonPath("$.content[0].street").value("Rua A"));
    }

    @Test
    @DisplayName("Addresses not listed because account does not exist")
    void listFailsWhenAccountDoesNotExist() throws Exception {
        when(addressService.list(eq(authentication), any()))
                .thenThrow(new ResourceNotFoundException("Account not found"));

        mockMvc.perform(get(BASE_URL).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    // ---------------------------------------------------------------- update

    @Test
    @DisplayName("Address updated with success")
    void update() throws Exception {
        UUID addressId = UUID.randomUUID();
        AddressResponse response = response(addressId);
        when(addressService.update(authentication, addressId, addressRequest())).thenReturn(response);

        mockMvc.perform(put(BASE_URL + "/" + addressId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Rua A", "SP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(addressId.toString()));
    }

    @Test
    @DisplayName("Address not updated because address does not exist")
    void updateFailsWhenAddressDoesNotExist() throws Exception {
        UUID addressId = UUID.randomUUID();
        when(addressService.update(eq(authentication), eq(addressId), any()))
                .thenThrow(new ResourceNotFoundException("Address not found"));

        mockMvc.perform(put(BASE_URL + "/" + addressId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Rua A", "SP")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Address not found"));
    }

    @Test
    @DisplayName("Address not updated because body is invalid")
    void updateFailsWhenBodyIsInvalid() throws Exception {
        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("", "SP")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(addressService);
    }

    @Test
    @DisplayName("Address not updated because address id is not a UUID")
    void updateFailsWhenAddressIdIsNotAUuid() throws Exception {
        mockMvc.perform(put(BASE_URL + "/not-a-uuid")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Rua A", "SP")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(addressService);
    }

    @Test
    @DisplayName("Address not updated because address is registered")
    void updateFailsWhenAddressIsAlreadyRegistered() throws Exception {
        when(addressService.update(any(), any(), any())).thenThrow(new RuleConflit("Address is already registered"));

        mockMvc.perform(put(BASE_URL + "/" + UUID.randomUUID())
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("Rua A", "SP")))
                .andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- delete

    @Test
    @DisplayName("Address deleted with success returns 204")
    void delete() throws Exception {
        UUID addressId = UUID.randomUUID();

        mockMvc.perform(MockMvcRequestBuilders.delete(BASE_URL + "/" + addressId).principal(authentication))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(addressService).delete(authentication, addressId);
    }

    @Test
    @DisplayName("Address not deleted because address does not exist")
    void deleteFailsWhenAddressDoesNotExist() throws Exception {
        UUID addressId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Address not found"))
                .when(addressService).delete(authentication, addressId);

        mockMvc.perform(MockMvcRequestBuilders.delete(BASE_URL + "/" + addressId).principal(authentication))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Address not found"));
    }

    // ------------------------------------------------------------- lookupCep

    @Test
    @DisplayName("Zip code found with success")
    void lookupCep() throws Exception {
        when(viaCepClient.lookup("13000000")).thenReturn(
                new ZipCodeResponse("13000-000", "Rua A", "", "Bairro", "Campinas", "SP", true));

        mockMvc.perform(get(BASE_URL + "/cep/13000000").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zip_code").value("13000-000"))
                .andExpect(jsonPath("$.street").value("Rua A"))
                .andExpect(jsonPath("$.city").value("Campinas"))
                .andExpect(jsonPath("$.found").value(true));
    }

    @Test
    @DisplayName("Zip code not found returns found false")
    void lookupCepNotFound() throws Exception {
        when(viaCepClient.lookup("00000000"))
                .thenReturn(new ZipCodeResponse("00000000", null, null, null, null, null, false));

        mockMvc.perform(get(BASE_URL + "/cep/00000000").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.street").doesNotExist());
    }

    // --------------------------------------------------------------- helpers

    private static AddressRequest addressRequest() {
        return new AddressRequest("13000000", "Rua A", "20", "Apto 1", "Bairro", "Campinas", "SP", true);
    }

    private static AddressResponse response(UUID id) {
        return new AddressResponse(id, "13000000", "Rua A", "20", "Apto 1", "Bairro", "Campinas", "SP", true);
    }

    private static String addressJson(String street, String state) {
        return """
                {"zip_code": "13000000", "street": "%s", "number": "20", "complement": "Apto 1",
                 "neighborhood": "Bairro", "city": "Campinas", "state": "%s", "main": true}""".formatted(street, state);
    }
}
