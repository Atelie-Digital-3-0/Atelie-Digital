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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.hamcrest.Matchers.containsString;
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
                "(84) 99999-9999",
                "contato@atelieartesanal.com",
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
                   "phone": "(84) 99999-9999",
                   "email": "contato@atelieartesanal.com",
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
        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                validCreateStoreJson().getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "store.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );

        MockMultipartFile banner = new MockMultipartFile(
                "banner",
                "banner.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{4, 5, 6}
        );

        StorePublicResponse response = buildStoreResponse();

        when(storeService.createStore(
                eq(artisanId),
                any(CreateStoreRequest.class),
                any(MultipartFile.class),
                any(MultipartFile.class)
        )).thenReturn(response);

        mockMvc.perform(multipart("/api/stores")
                        .file(requestPart)
                        .file(image)
                        .file(banner)
                        .principal(authentication))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(header().string(
                        "Location",
                        containsString(storeId.toString())
                ));

        verify(storeService).createStore(
                eq(artisanId),
                any(CreateStoreRequest.class),
                any(MultipartFile.class),
                any(MultipartFile.class)
        );
    }


    @Test
    void shouldReturnBadRequestWhenCreateStoreRequestIsInvalid()
            throws Exception {

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

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                invalidJson.getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile image = new MockMultipartFile(
                "image",
                "store.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );

        MockMultipartFile banner = new MockMultipartFile(
                "banner",
                "banner.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{4, 5, 6}
        );

        mockMvc.perform(multipart("/api/stores")
                        .file(requestPart)
                        .file(image)
                        .file(banner)
                        .principal(authentication))
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
                .andExpect(jsonPath("$.phone").value("(84) 99999-9999"))
                .andExpect(jsonPath("$.email").value("contato@atelieartesanal.com"))
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
                .andExpect(jsonPath("$.name").value("Ateliê Artesanal"))
                .andExpect(jsonPath("$.phone").value("(84) 99999-9999"))
                .andExpect(jsonPath("$.email").value("contato@atelieartesanal.com"));

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

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                updateJson.getBytes(StandardCharsets.UTF_8)
        );

        when(storeService.updateStore(
                eq(storeId),
                eq(artisanId),
                any(UpdateStoreRequest.class),
                isNull(),
                isNull()
        )).thenReturn(response);

        mockMvc.perform(multipart("/api/stores/{storeId}", storeId)
                        .file(requestPart)
                        .principal(authentication)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(storeId.toString()))
                .andExpect(jsonPath("$.name").value("Ateliê Artesanal"));

        verify(storeService).updateStore(
                eq(storeId),
                eq(artisanId),
                any(UpdateStoreRequest.class),
                isNull(),
                isNull()
        );
    }

    @Test
    void shouldReturnBadRequestWhenUpdateDescriptionExceedsLimit()
            throws Exception {

        String longDescription = "a".repeat(501);

        String invalidJson = """
            {
                "description": "%s"
            }
            """.formatted(longDescription);

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                invalidJson.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/stores/{storeId}", storeId)
                        .file(requestPart)
                        .principal(authentication)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        }))
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
