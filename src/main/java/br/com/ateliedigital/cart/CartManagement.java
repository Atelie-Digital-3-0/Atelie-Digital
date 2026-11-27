package br.com.ateliedigital.cart;

import br.com.ateliedigital.cart.service.CartService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * API publica do modulo cart para os demais modulos (os subpacotes sao internos ao modulo).
 */
@Service
public class CartManagement {
    private final CartService cartService;

    public CartManagement(CartService cartService) {
        this.cartService = cartService;
    }

    /**
     * Esvazia o carrinho do usuario. Deve ser chamado pelo checkout/pedidos apos o pedido ser criado com sucesso.
     * Idempotente.
     *
     * @param keycloakId {@code sub} do JWT do dono do carrinho
     */
    public void clear(UUID keycloakId) {
        cartService.clear(keycloakId);
    }
}
