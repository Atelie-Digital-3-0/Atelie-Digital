package br.com.ateliedigital.cart.controller;

import br.com.ateliedigital.cart.dto.CartRequests.AddCartItemRequest;
import br.com.ateliedigital.cart.dto.CartRequests.UpdateCartItemRequest;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemResponse;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemStatus;
import br.com.ateliedigital.cart.dto.CartResponses.CartResponse;
import br.com.ateliedigital.cart.service.CartService;
import br.com.ateliedigital.exceptions.GlobalExceptionHandler;
import br.com.ateliedigital.exceptions.VioletedRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {
    private static final String BASE_URL = "/api/carts";

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
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", userId.toString()));
        authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    @Test
    @DisplayName("Cart of the token owner is returned in snake_case")
    void getCart() throws Exception {
        when(cartService.getCart(userId)).thenReturn(
                new CartResponse(List.of(item(2)), 1, 2, new BigDecimal("99.80"), true, List.of()));

        mockMvc.perform(get(BASE_URL).principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].product_variant_id").value("7"))
                .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.total_quantity").value(2))
                .andExpect(jsonPath("$.total_price").value(99.80))
                .andExpect(jsonPath("$.removed_items").isEmpty());
    }

    @Test
    @DisplayName("Item added returns 201")
    void addItem() throws Exception {
        when(cartService.addItem(userId, new AddCartItemRequest("7", 2))).thenReturn(item(2));

        mockMvc.perform(post(BASE_URL + "/items").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_variant_id\": \"7\", \"quantity\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.subtotal").value(99.80));
    }

    @Test
    @DisplayName("Item not added because quantity is zero")
    void addItemFailsWhenQuantityIsZero() throws Exception {
        mockMvc.perform(post(BASE_URL + "/items").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_variant_id\": \"7\", \"quantity\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("quantity must be greater than zero"));

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("Item not added because product_variant_id has invalid characters")
    void addItemFailsWhenVariantIdIsInvalid() throws Exception {
        mockMvc.perform(post(BASE_URL + "/items").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_variant_id\": \"7:*\", \"quantity\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("product_variant_id is invalid"));

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("Item not added because of a business rule returns 409")
    void addItemFailsWithBusinessRule() throws Exception {
        when(cartService.addItem(eq(userId), any()))
                .thenThrow(new VioletedRule("Insufficient stock: only 1 units available"));

        mockMvc.perform(post(BASE_URL + "/items").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_variant_id\": \"7\", \"quantity\": 5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock: only 1 units available"));
    }

    @Test
    @DisplayName("Item quantity updated returns 200")
    void updateItem() throws Exception {
        when(cartService.updateItem(userId, "7", new UpdateCartItemRequest(3))).thenReturn(Optional.of(item(3)));

        mockMvc.perform(patch(BASE_URL + "/items/7").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));
    }

    @Test
    @DisplayName("Quantity zero removes the item and returns 204")
    void updateItemToZero() throws Exception {
        when(cartService.updateItem(userId, "7", new UpdateCartItemRequest(0))).thenReturn(Optional.empty());

        mockMvc.perform(patch(BASE_URL + "/items/7").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 0}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Item not updated because quantity is negative")
    void updateItemFailsWhenQuantityIsNegative() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/items/7").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": -1}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @DisplayName("Item removed returns 204")
    void removeItem() throws Exception {
        mockMvc.perform(delete(BASE_URL + "/items/7").principal(authentication))
                .andExpect(status().isNoContent());

        verify(cartService).removeItem(userId, "7");
    }

    @Test
    @DisplayName("Cart cleared returns 204")
    void clearCart() throws Exception {
        mockMvc.perform(delete(BASE_URL).principal(authentication))
                .andExpect(status().isNoContent());

        verify(cartService).clear(userId);
    }

    private static CartItemResponse item(int quantity) {
        BigDecimal price = new BigDecimal("49.90");
        return new CartItemResponse("7", "70", "700", "SKU-7", "azul", "M", quantity, price, null,
                price.multiply(BigDecimal.valueOf(quantity)), 10, CartItemStatus.AVAILABLE,
                Instant.now(), Instant.now());
    }
}
