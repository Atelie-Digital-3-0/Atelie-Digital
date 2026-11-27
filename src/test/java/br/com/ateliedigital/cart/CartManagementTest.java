package br.com.ateliedigital.cart;

import br.com.ateliedigital.cart.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/** API publica do modulo usada pelo checkout/pedidos. */
@ExtendWith(MockitoExtension.class)
class CartManagementTest {
    @Mock
    private CartService cartService;

    @InjectMocks
    private CartManagement cartManagement;

    @Test
    @DisplayName("clear delega ao servico com o keycloakId do dono do carrinho")
    void clearDelegatesToService() {
        UUID keycloakId = UUID.randomUUID();

        cartManagement.clear(keycloakId);

        verify(cartService).clear(keycloakId);
        verifyNoMoreInteractions(cartService);
    }
}
