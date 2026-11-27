package br.com.ateliedigital.product.unit.controller;

import br.com.ateliedigital.product.controller.ProductController;
import br.com.ateliedigital.product.dto.responses.ProductResponses;
import br.com.ateliedigital.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductControllerTest {

    private ProductService productService;
    private MockMvc mockMvc;
    private UUID artisanId;
    private UUID productId;
    private UUID variationId;
    private UUID imageId;
    private JwtAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ProductController(productService)
        ).build();

        artisanId = UUID.randomUUID();
        productId = UUID.randomUUID();
        variationId = UUID.randomUUID();
        imageId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(artisanId.toString())
                .build();

        authentication = new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ARTISAN"))
        );
    }

    @Test
    void shouldUploadImagesForVariation() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "images",
                "produto.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );

        ProductResponses.ProductImageResponse response =
                new ProductResponses.ProductImageResponse(
                        imageId,
                        "http://localhost/produto.png",
                        0,
                        null
                );

        when(productService.addImages(
                eq(artisanId),
                eq(productId),
                eq(variationId),
                anyList()
        )).thenReturn(List.of(response));

        mockMvc.perform(multipart(
                        "/api/products/{productId}/variations/{variationId}/images",
                        productId,
                        variationId
                )
                        .file(image)
                        .principal(authentication))
                .andExpect(status().isOk());

        verify(productService).addImages(
                eq(artisanId),
                eq(productId),
                eq(variationId),
                anyList()
        );
    }

    @Test
    void shouldDeleteImageFromVariation() throws Exception {
        mockMvc.perform(delete(
                        "/api/products/{productId}/variations/{variationId}/images/{imageId}",
                        productId,
                        variationId,
                        imageId
                ).principal(authentication))
                .andExpect(status().isNoContent());

        verify(productService).deleteImage(
                artisanId,
                productId,
                variationId,
                imageId
        );
    }
}
