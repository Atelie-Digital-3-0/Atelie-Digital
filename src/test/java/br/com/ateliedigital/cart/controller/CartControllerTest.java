package br.com.ateliedigital.cart.controller;

import br.com.ateliedigital.cart.dto.CartRequests.AddCartItemRequest;
import br.com.ateliedigital.cart.dto.CartRequests.UpdateCartItemRequest;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemResponse;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemStatus;
import br.com.ateliedigital.cart.dto.CartResponses.CartResponse;
import br.com.ateliedigital.cart.dto.CartResponses.RemovalReason;
import br.com.ateliedigital.cart.dto.CartResponses.RemovedCartItem;
import br.com.ateliedigital.cart.service.CartService;
import br.com.ateliedigital.exceptions.GlobalExceptionHandler;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
import br.com.ateliedigital.exceptions.VioletedRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes unitarios da camada HTTP do carrinho: MockMvc standalone (sem contexto Spring), servico mockado e o
 * {@link GlobalExceptionHandler} real, para conferir o mapeamento excecao -> status HTTP.
 * <p>
 * Tecnicas: <b>classes de equivalencia</b> dos corpos (valido, malformado, campo ausente, valor fora do dominio)
 * e uma linha por status documentado de cada rota (200/201/204/400/401/404/409/503).
 * Autenticacao/autorizacao real (401 sem token, 403 por papel) fica no {@code CartApiIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class CartControllerTest {
    private static final String BASE_URL = "/api/carts";
    private static final Instant ADDED = Instant.parse("2026-11-19T10:00:00Z");

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController controller;

    private MockMvc mockMvc;
    private JwtAuthenticationToken authentication;
    private UUID userId;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .build();

        userId = UUID.randomUUID();
        authentication = token(userId.toString());
    }

    // =====================================================================================================
    @Nested
    @DisplayName("GET /api/carts")
    class GetCart {

        @Test
        @DisplayName("200 com o carrinho do dono do token em snake_case")
        void returnsCartInSnakeCase() throws Exception {
            CartItemResponse changed = new CartItemResponse("7", "70", "700", "SKU-7", "azul", "M", 2,
                    new BigDecimal("59.90"), new BigDecimal("49.90"), new BigDecimal("119.80"), 10,
                    CartItemStatus.PRICE_CHANGED, ADDED, ADDED);
            when(cartService.getCart(userId)).thenReturn(new CartResponse(List.of(changed), 1, 2,
                    new BigDecimal("119.80"), true,
                    List.of(new RemovedCartItem("8", RemovalReason.PRODUCT_UNAVAILABLE))));

            mockMvc.perform(get(BASE_URL).principal(authentication))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].product_variant_id").value("7"))
                    .andExpect(jsonPath("$.items[0].store_id").value("700"))
                    .andExpect(jsonPath("$.items[0].status").value("PRICE_CHANGED"))
                    .andExpect(jsonPath("$.items[0].unit_price").value(59.90))
                    .andExpect(jsonPath("$.items[0].previous_unit_price").value(49.90))
                    .andExpect(jsonPath("$.items[0].available_stock").value(10))
                    .andExpect(jsonPath("$.total_items").value(1))
                    .andExpect(jsonPath("$.total_quantity").value(2))
                    .andExpect(jsonPath("$.total_price").value(119.80))
                    .andExpect(jsonPath("$.verified").value(true))
                    .andExpect(jsonPath("$.removed_items[0].product_variant_id").value("8"))
                    .andExpect(jsonPath("$.removed_items[0].reason").value("PRODUCT_UNAVAILABLE"));
        }

        @Test
        @DisplayName("401 quando o sub do JWT nao e um UUID do Keycloak (servico nao e chamado)")
        void rejectsNonUuidSubject() throws Exception {
            mockMvc.perform(get(BASE_URL).principal(token("service-account")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("JWT subject is not a Keycloak UUID"));

            verifyNoInteractions(cartService);
        }
    }

    // =====================================================================================================
    @Nested
    @DisplayName("POST /api/carts/items")
    class AddItem {

        @Test
        @DisplayName("201 com o item criado ou somado")
        void created() throws Exception {
            when(cartService.addItem(userId, new AddCartItemRequest("7", 2))).thenReturn(item(2));

            mockMvc.perform(json(post(BASE_URL + "/items"), "{\"product_variant_id\": \"7\", \"quantity\": 2}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.product_variant_id").value("7"))
                    .andExpect(jsonPath("$.quantity").value(2))
                    .andExpect(jsonPath("$.subtotal").value(99.80));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {
                "{\"product_variant_id\": \"7\", \"quantity\": 0}",
                "{\"product_variant_id\": \"7\", \"quantity\": -3}",
                "{\"product_variant_id\": \"7\"}",
                "{\"quantity\": 1}",
                "{\"product_variant_id\": \"\", \"quantity\": 1}",
                "{\"product_variant_id\": \"7:*\", \"quantity\": 1}",
                "{\"product_variant_id\": \"7\", \"quantity\": \"dois\"}",
                "{\"product_variant_id\": \"7\", \"quantity\": 1",
                ""
        })
        @DisplayName("400 para corpo invalido, sem chamar o servico")
        void badRequest(String body) throws Exception {
            mockMvc.perform(json(post(BASE_URL + "/items"), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.path").value(BASE_URL + "/items"));

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("400 traz a mensagem da regra violada")
        void badRequestMessage() throws Exception {
            mockMvc.perform(json(post(BASE_URL + "/items"), "{\"product_variant_id\": \"7\", \"quantity\": 0}"))
                    .andExpect(jsonPath("$.message").value("quantity must be greater than zero"));
            mockMvc.perform(json(post(BASE_URL + "/items"), "{\"product_variant_id\": \"7:*\", \"quantity\": 1}"))
                    .andExpect(jsonPath("$.message").value("product_variant_id is invalid"));
        }

        @Test
        @DisplayName("404 quando o produto nao existe no catalogo")
        void productNotFound() throws Exception {
            when(cartService.addItem(eq(userId), any())).thenThrow(new ResourceNotFoundException("Product not found"));

            mockMvc.perform(json(post(BASE_URL + "/items"), "{\"product_variant_id\": \"7\", \"quantity\": 1}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product not found"));
        }

        @Test
        @DisplayName("409 quando uma regra de negocio e violada (estoque, limites)")
        void businessRule() throws Exception {
            when(cartService.addItem(eq(userId), any()))
                    .thenThrow(new VioletedRule("Insufficient stock: only 1 units available"));

            mockMvc.perform(json(post(BASE_URL + "/items"), "{\"product_variant_id\": \"7\", \"quantity\": 5}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Insufficient stock: only 1 units available"));
        }

        @Test
        @DisplayName("409 quando o carrinho foi alterado concorrentemente alem das retentativas")
        void concurrentModification() throws Exception {
            when(cartService.addItem(eq(userId), any()))
                    .thenThrow(new RuleConflit("Cart was modified concurrently, please try again"));

            mockMvc.perform(json(post(BASE_URL + "/items"), "{\"product_variant_id\": \"7\", \"quantity\": 1}"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("503 quando o catalogo esta indisponivel")
        void catalogUnavailable() throws Exception {
            when(cartService.addItem(eq(userId), any())).thenThrow(
                    new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog service unavailable"));

            mockMvc.perform(json(post(BASE_URL + "/items"), "{\"product_variant_id\": \"7\", \"quantity\": 1}"))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    // =====================================================================================================
    @Nested
    @DisplayName("PATCH /api/carts/items/{id}")
    class UpdateItem {

        @Test
        @DisplayName("200 com o item atualizado")
        void updated() throws Exception {
            when(cartService.updateItem(userId, "7", new UpdateCartItemRequest(3))).thenReturn(Optional.of(item(3)));

            mockMvc.perform(json(patch(BASE_URL + "/items/7"), "{\"quantity\": 3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.quantity").value(3));
        }

        @Test
        @DisplayName("204 sem corpo quando quantity = 0 removeu o item")
        void zeroRemoves() throws Exception {
            when(cartService.updateItem(userId, "7", new UpdateCartItemRequest(0))).thenReturn(Optional.empty());

            mockMvc.perform(json(patch(BASE_URL + "/items/7"), "{\"quantity\": 0}"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"{\"quantity\": -1}", "{}", "{\"quantity\": null}", "nao e json"})
        @DisplayName("400 para quantidade negativa, ausente ou corpo malformado")
        void badRequest(String body) throws Exception {
            mockMvc.perform(json(patch(BASE_URL + "/items/7"), body))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(cartService);
        }

        @Test
        @DisplayName("404 quando o item nao esta no carrinho")
        void notInCart() throws Exception {
            when(cartService.updateItem(eq(userId), eq("7"), any()))
                    .thenThrow(new ResourceNotFoundException("Product is not in the cart"));

            mockMvc.perform(json(patch(BASE_URL + "/items/7"), "{\"quantity\": 2}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Product is not in the cart"));
        }

        @Test
        @DisplayName("Id da variante invalido na URL nao chega ao servico")
        void invalidPathId() throws Exception {
            mockMvc.perform(json(patch(BASE_URL + "/items/{id}", "7:*"), "{\"quantity\": 2}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(cartService);
        }
    }

    // =====================================================================================================
    @Nested
    @DisplayName("DELETE /api/carts/items/{id} e DELETE /api/carts")
    class Delete {

        @Test
        @DisplayName("204 quando o item e removido")
        void removed() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/items/7").principal(authentication))
                    .andExpect(status().isNoContent());

            verify(cartService).removeItem(userId, "7");
        }

        @Test
        @DisplayName("404 quando o item nao estava no carrinho")
        void notInCart() throws Exception {
            doThrow(new ResourceNotFoundException("Product is not in the cart"))
                    .when(cartService).removeItem(userId, "7");

            mockMvc.perform(delete(BASE_URL + "/items/7").principal(authentication))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Id da variante invalido na URL nao chega ao servico")
        void invalidPathId() throws Exception {
            mockMvc.perform(delete(BASE_URL + "/items/{id}", "a".repeat(65)).principal(authentication))
                    .andExpect(status().isBadRequest());

            verify(cartService, org.mockito.Mockito.never()).removeItem(any(), anyString());
        }

        @Test
        @DisplayName("204 ao limpar o carrinho")
        void clear() throws Exception {
            mockMvc.perform(delete(BASE_URL).principal(authentication))
                    .andExpect(status().isNoContent());

            verify(cartService).clear(userId);
        }
    }

    // ------------------------------------------------------------------------------------------- helpers

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.principal(authentication).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static JwtAuthenticationToken token(String subject) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", subject));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    private static CartItemResponse item(int quantity) {
        BigDecimal price = new BigDecimal("49.90");
        return new CartItemResponse("7", "70", "700", "SKU-7", "azul", "M", quantity, price, null,
                price.multiply(BigDecimal.valueOf(quantity)), 10, CartItemStatus.AVAILABLE, ADDED, ADDED);
    }
}
