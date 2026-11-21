package br.com.ateliedigital.store.unit.controller;
import br.com.ateliedigital.exceptions.GlobalExceptionHandler;
import br.com.ateliedigital.store.controller.StoreController;
import br.com.ateliedigital.store.dto.requests.StoreRequests.CreateStoreRequest;
import br.com.ateliedigital.store.dto.requests.StoreRequests.UpdateStoreRequest;
import br.com.ateliedigital.store.dto.responses.StoreAddressResponses.AddressResponse;
import br.com.ateliedigital.store.dto.responses.StoreResponses.CategoryResponse;
import br.com.ateliedigital.store.dto.responses.StoreResponses.StorePublicResponse;
import br.com.ateliedigital.store.service.StoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class StoreControllerTest {



    private StoreService storeService;
    private MockMvc mockMvc;

    private UUID artisanId;
    private UUID storeId;
    private UUID categoryId;
    private UUID addressId;

    private JwtAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        storeService = mock(StoreService.class);

        StoreController controller = new StoreController(storeService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        artisanId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        addressId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(artisanId.toString())
                .build();

        authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ARTISAN"))
        );
    }

    private StorePublicResponse buildStoreResponse() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 21, 10, 0);

        return new StorePublicResponse(
                storeId,
                artisanId,
                "Ateliê Artesanal",
                "Produtos feitos à mão",
                new CategoryResponse(categoryId, "Artesanato"),
                null,
                null,
                new AddressResponse(
                        addressId,
                        "Rua Principal",
                        "100",
                        "Centro",
                        "Pau dos Ferros",
                        "RN",
                        "59900-000",
                        null
                ),
                now,
                now
        );
    }

    private String validCreateStoreJson() {
        return """
                {
                    "name": "Ateliê Artesanal",
                    "description": "Produtos feitos à mão",
                    "categoryId": "%s",
                    "address": {
                        "street": "Rua Principal",
                        "number": "100",
                        "neighborhood": "Centro",
                        "city": "Pau dos Ferros",
                        "state": "RN",
                        "zipCode": "59900-000",
                        "complement": "Próximo à praça"
                    }
                }
                """.formatted(categoryId);
    }

    // CREATE

    @Test
    void shouldCreateStoreAndReturnCreatedResponse() throws Exception {
        StorePublicResponse response = buildStoreResponse();

        when(storeService.createStore(
                eq(artisanId),
                any(CreateStoreRequest.class)
        )).thenReturn(response);

        mockMvc.perform(post("/api/stores")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateStoreJson()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.endsWith("/api/stores/" + storeId)
                ))
                .andExpect(jsonPath("$.id").value(storeId.toString()))
                .andExpect(jsonPath("$.artisanId").value(artisanId.toString()))
                .andExpect(jsonPath("$.name").value("Ateliê Artesanal"))
                .andExpect(jsonPath("$.description").value("Produtos feitos à mão"))
                .andExpect(jsonPath("$.category.name").value("Artesanato"))
                .andExpect(jsonPath("$.address.zipCode").value("59900-000"));

        verify(storeService).createStore(
                eq(artisanId),
                any(CreateStoreRequest.class)
        );
    }

    @Test
    void shouldReturnBadRequestWhenCreateStoreRequestIsInvalid() throws Exception {
        String invalidJson = """
                {
                    "name": "",
                    "description": "Descrição",
                    "categoryId": "%s",
                    "address": {
                        "street": "Rua Principal",
                        "number": "100",
                        "neighborhood": "Centro",
                        "city": "Pau dos Ferros",
                        "state": "RN",
                        "zipCode": "59900-000"
                    }
                }
                """.formatted(categoryId);

        mockMvc.perform(post("/api/stores")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(storeService);
    }

    // GET BY ID

    @Test
    void shouldGetStoreById() throws Exception {
        StorePublicResponse response = buildStoreResponse();

        when(storeService.getStoreById(storeId)).thenReturn(response);

        mockMvc.perform(get("/api/stores/{storeId}", storeId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(storeId.toString()))
                .andExpect(jsonPath("$.name").value("Ateliê Artesanal"))
                .andExpect(jsonPath("$.category.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.address.city").value("Pau dos Ferros"));

        verify(storeService).getStoreById(storeId);
    }

    // GET MY STORE

    @Test
    void shouldGetAuthenticatedArtisanStore() throws Exception {
        StorePublicResponse response = buildStoreResponse();

        when(storeService.getMyStore(artisanId)).thenReturn(response);

        mockMvc.perform(get("/api/stores/me")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storeId.toString()))
                .andExpect(jsonPath("$.artisanId").value(artisanId.toString()))
                .andExpect(jsonPath("$.name").value("Ateliê Artesanal"));

        verify(storeService).getMyStore(artisanId);
    }

    // UPDATE

    @Test
    void shouldUpdateStore() throws Exception {
        StorePublicResponse response = buildStoreResponse();

        String updateJson = """
                {
                    "description": "Nova descrição da loja"
                }
                """;

        when(storeService.updateStore(
                eq(storeId),
                eq(artisanId),
                any(UpdateStoreRequest.class)
        )).thenReturn(response);

        mockMvc.perform(patch("/api/stores/{storeId}", storeId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storeId.toString()))
                .andExpect(jsonPath("$.name").value("Ateliê Artesanal"));

        verify(storeService).updateStore(
                eq(storeId),
                eq(artisanId),
                any(UpdateStoreRequest.class)
        );
    }

    @Test
    void shouldReturnBadRequestWhenUpdateDescriptionExceedsLimit() throws Exception {
        String longDescription = "a".repeat(501);

        String invalidJson = """
                {
                    "description": "%s"
                }
                """.formatted(longDescription);

        mockMvc.perform(patch("/api/stores/{storeId}", storeId)
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(storeService);
    }

    // DELETE

    @Test
    void shouldDeleteStoreAndReturnNoContent() throws Exception {
        doNothing().when(storeService).deleteStore(storeId, artisanId);

        mockMvc.perform(delete("/api/stores/{storeId}", storeId)
                        .principal(authentication))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(storeService).deleteStore(storeId, artisanId);
    }
}
