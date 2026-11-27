package br.com.ateliedigital.cart.controller;

import br.com.ateliedigital.cart.dto.CartRequests;
import br.com.ateliedigital.cart.dto.CartRequests.AddCartItemRequest;
import br.com.ateliedigital.cart.dto.CartRequests.UpdateCartItemRequest;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemResponse;
import br.com.ateliedigital.cart.dto.CartResponses.CartResponse;
import br.com.ateliedigital.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Carrinho do usuario autenticado. O dono do carrinho e sempre o {@code sub} do JWT do Keycloak: nao existe
 * id de carrinho na URL, entao um usuario nunca acessa o carrinho de outro.
 */
@RestController
@RequestMapping("/api/carts")
@PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN')")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ResponseEntity<CartResponse> get(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok(cartService.getCart(userId(authentication)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addItem(JwtAuthenticationToken authentication,
                                                    @Valid @RequestBody AddCartItemRequest request) {
        CartItemResponse response = cartService.addItem(userId(authentication), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Responde {@code 200} com o item atualizado, ou {@code 204} quando {@code quantity = 0} removeu o item. */
    @PatchMapping("/items/{productVariantId}")
    public ResponseEntity<CartItemResponse> updateItem(
            JwtAuthenticationToken authentication,
            @PathVariable @Pattern(regexp = CartRequests.VARIANT_ID_PATTERN) String productVariantId,
            @Valid @RequestBody UpdateCartItemRequest request
    ) {
        return cartService.updateItem(userId(authentication), productVariantId, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @DeleteMapping("/items/{productVariantId}")
    public ResponseEntity<Void> removeItem(
            JwtAuthenticationToken authentication,
            @PathVariable @Pattern(regexp = CartRequests.VARIANT_ID_PATTERN) String productVariantId
    ) {
        cartService.removeItem(userId(authentication), productVariantId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(JwtAuthenticationToken authentication) {
        cartService.clear(userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(JwtAuthenticationToken authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is not a Keycloak UUID");
        }
    }
}
