package br.com.ateliedigital.cart.service;

import br.com.ateliedigital.cart.config.CartProperties;
import br.com.ateliedigital.cart.dto.CartRequests.AddCartItemRequest;
import br.com.ateliedigital.cart.dto.CartRequests.UpdateCartItemRequest;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemResponse;
import br.com.ateliedigital.cart.dto.CartResponses.CartItemStatus;
import br.com.ateliedigital.cart.dto.CartResponses.CartResponse;
import br.com.ateliedigital.cart.dto.CartResponses.RemovalReason;
import br.com.ateliedigital.cart.integration.catalog.ProductCatalog;
import br.com.ateliedigital.cart.integration.catalog.ProductSnapshot;
import br.com.ateliedigital.cart.integration.catalog.VariantLookup;
import br.com.ateliedigital.cart.mapper.CartMapper;
import br.com.ateliedigital.cart.model.CartItem;
import br.com.ateliedigital.cart.repository.CartRepository;
import br.com.ateliedigital.cart.repository.CartRepository.ItemChange;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.VioletedRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
    private static final Instant ADDED = Instant.parse("2026-11-19T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductCatalog productCatalog;

    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(cartRepository, productCatalog, new CartMapper(),
                new CartProperties(10, 2, "cart:"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------- addItem

    @Test
    @DisplayName("New item is added with the catalog snapshot")
    void addNewItem() {
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.found(product("7", "49.90", 5)));
        simulateSave(null, 0);

        CartItemResponse response = service.addItem(USER, new AddCartItemRequest("7", 2));

        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.unitPrice()).isEqualByComparingTo("49.90");
        assertThat(response.subtotal()).isEqualByComparingTo("99.80");
        assertThat(response.status()).isEqualTo(CartItemStatus.AVAILABLE);
        assertThat(response.addedAt()).isEqualTo(NOW);
        assertThat(savedItem().storeId()).isEqualTo("store-1");
    }

    @Test
    @DisplayName("Adding an existing item sums the quantity, keeps added_at and refreshes the price")
    void addExistingItemSumsQuantity() {
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.found(product("7", "55.00", 5)));
        simulateSave(item("7", 2, "49.90"), 1);

        CartItemResponse response = service.addItem(USER, new AddCartItemRequest("7", 3));

        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.unitPrice()).isEqualByComparingTo("55.00");
        assertThat(response.addedAt()).isEqualTo(ADDED);
        assertThat(response.updatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Item not added because the summed quantity exceeds the stock")
    void addFailsWhenStockIsInsufficient() {
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.found(product("7", "49.90", 3)));
        simulateSave(item("7", 2, "49.90"), 1);

        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 2)))
                .isInstanceOf(VioletedRule.class)
                .hasMessage("Insufficient stock: only 3 units available");
    }

    @Test
    @DisplayName("Item not added because the quantity exceeds the per-item limit")
    void addFailsWhenQuantityLimitIsExceeded() {
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.found(product("7", "49.90", 500)));
        simulateSave(null, 0);

        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 11)))
                .isInstanceOf(VioletedRule.class)
                .hasMessage("Quantity per item cannot exceed 10");
    }

    @Test
    @DisplayName("Item not added because the cart already has the maximum of different items")
    void addFailsWhenDistinctItemLimitIsReached() {
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.found(product("7", "49.90", 5)));
        simulateSave(null, 2);

        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 1)))
                .isInstanceOf(VioletedRule.class)
                .hasMessage("Cart cannot have more than 2 different items");
    }

    @Test
    @DisplayName("Item not added because the product does not exist in the catalog")
    void addFailsWhenProductIsMissing() {
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.missing());

        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 1)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product not found");
        verify(cartRepository, never()).save(any(), anyString(), any());
    }

    @Test
    @DisplayName("Item not added because the catalog is unavailable")
    void addFailsWhenCatalogIsUnavailable() {
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.failed("timeout"));

        assertThatThrownBy(() -> service.addItem(USER, new AddCartItemRequest("7", 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(cartRepository, never()).save(any(), anyString(), any());
    }

    // ------------------------------------------------------------------- getCart

    @Test
    @DisplayName("Empty cart returns zero totals without calling the catalog")
    void getEmptyCart() {
        when(cartRepository.findAll(USER)).thenReturn(List.of());

        CartResponse cart = service.getCart(USER);

        assertThat(cart.items()).isEmpty();
        assertThat(cart.totalPrice()).isEqualByComparingTo("0");
        assertThat(cart.verified()).isTrue();
        verify(productCatalog, never()).lookupAll(anyCollection());
    }

    @Test
    @DisplayName("Cart is reconciled with the catalog: status per item, totals and removal of unavailable items")
    void getCartReconcilesWithCatalog() {
        when(cartRepository.findAll(USER)).thenReturn(List.of(
                item("1", 2, "10.00"),   // preco igual
                item("2", 1, "20.00"),   // preco mudou
                item("3", 4, "5.00"),    // estoque insuficiente
                item("4", 1, "8.00"),    // produto excluido
                item("5", 3, "1.50")));  // catalogo falhou
        Map<String, VariantLookup> lookups = new LinkedHashMap<>();
        lookups.put("1", VariantLookup.found(product("1", "10.00", 10)));
        lookups.put("2", VariantLookup.found(product("2", "25.00", 10)));
        lookups.put("3", VariantLookup.found(product("3", "5.00", 2)));
        lookups.put("4", VariantLookup.missing());
        lookups.put("5", VariantLookup.failed("timeout"));
        when(productCatalog.lookupAll(List.of("1", "2", "3", "4", "5"))).thenReturn(lookups);

        CartResponse cart = service.getCart(USER);

        assertThat(cart.items()).extracting(CartItemResponse::productVariantId, CartItemResponse::status)
                .containsExactly(
                        tuple("1", CartItemStatus.AVAILABLE),
                        tuple("2", CartItemStatus.PRICE_CHANGED),
                        tuple("3", CartItemStatus.INSUFFICIENT_STOCK),
                        tuple("5", CartItemStatus.UNVERIFIED));
        CartItemResponse changed = cart.items().get(1);
        assertThat(changed.unitPrice()).isEqualByComparingTo("25.00");
        assertThat(changed.previousUnitPrice()).isEqualByComparingTo("20.00");
        // Totais: itens 1 (2 x 10), 2 (1 x 25) e 5 (3 x 1.50, preco do snapshot); o item 3 fica de fora.
        assertThat(cart.totalQuantity()).isEqualTo(6);
        assertThat(cart.totalPrice()).isEqualByComparingTo("49.50");
        assertThat(cart.totalItems()).isEqualTo(4);
        assertThat(cart.verified()).isFalse();
        assertThat(cart.removedItems()).singleElement()
                .satisfies(removed -> {
                    assertThat(removed.productVariantId()).isEqualTo("4");
                    assertThat(removed.reason()).isEqualTo(RemovalReason.PRODUCT_UNAVAILABLE);
                });
        verify(cartRepository).removeAll(USER, List.of("4"));
    }

    // ---------------------------------------------------------------- updateItem

    @Test
    @DisplayName("Item quantity is replaced by the requested value")
    void updateItemQuantity() {
        when(cartRepository.exists(USER, "7")).thenReturn(true);
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.found(product("7", "49.90", 5)));
        simulateSave(item("7", 4, "49.90"), 1);

        Optional<CartItemResponse> response = service.updateItem(USER, "7", new UpdateCartItemRequest(1));

        assertThat(response).get().extracting(CartItemResponse::quantity).isEqualTo(1);
    }

    @Test
    @DisplayName("Quantity zero removes the item")
    void updateItemToZeroRemovesIt() {
        when(cartRepository.remove(USER, "7")).thenReturn(true);

        Optional<CartItemResponse> response = service.updateItem(USER, "7", new UpdateCartItemRequest(0));

        assertThat(response).isEmpty();
        verify(productCatalog, never()).lookup(anyString());
    }

    @Test
    @DisplayName("Item not updated because it is not in the cart")
    void updateFailsWhenItemIsNotInCart() {
        when(cartRepository.exists(USER, "7")).thenReturn(false);

        assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(2)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product is not in the cart");
        verify(productCatalog, never()).lookup(anyString());
    }

    @Test
    @DisplayName("Item not updated and removed because the product left the catalog")
    void updateRemovesItemWhenProductIsMissing() {
        when(cartRepository.exists(USER, "7")).thenReturn(true);
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.missing());

        assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(2)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Product is no longer available and was removed from the cart");
        verify(cartRepository).remove(USER, "7");
    }

    @Test
    @DisplayName("Item not updated because the requested quantity exceeds the stock")
    void updateFailsWhenStockIsInsufficient() {
        when(cartRepository.exists(USER, "7")).thenReturn(true);
        when(productCatalog.lookup("7")).thenReturn(VariantLookup.found(product("7", "49.90", 2)));
        simulateSave(item("7", 1, "49.90"), 1);

        assertThatThrownBy(() -> service.updateItem(USER, "7", new UpdateCartItemRequest(3)))
                .isInstanceOf(VioletedRule.class)
                .hasMessage("Insufficient stock: only 2 units available");
    }

    // -------------------------------------------------------------- remove/clear

    @Test
    @DisplayName("Item not removed because it is not in the cart")
    void removeFailsWhenItemIsNotInCart() {
        when(cartRepository.remove(USER, "7")).thenReturn(false);

        assertThatThrownBy(() -> service.removeItem(USER, "7"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Cart is cleared")
    void clearCart() {
        service.clear(USER);

        verify(cartRepository).clear(USER);
    }

    // ------------------------------------------------------------------- helpers

    /** Faz o mock do repositorio executar a alteracao sobre o estado informado, como o Redis faria. */
    private void simulateSave(CartItem current, long itemCount) {
        when(cartRepository.save(eq(USER), anyString(), any())).thenAnswer(invocation -> {
            ItemChange change = invocation.getArgument(2);
            return change.apply(current, itemCount);
        });
    }

    private CartItem savedItem() {
        ArgumentCaptor<ItemChange> change = ArgumentCaptor.forClass(ItemChange.class);
        verify(cartRepository).save(eq(USER), anyString(), change.capture());
        return change.getValue().apply(null, 0);
    }

    private static ProductSnapshot product(String id, String price, int stock) {
        return new ProductSnapshot(id, "product-" + id, "store-1", new BigDecimal(price), stock, "SKU-" + id,
                "azul", "M");
    }

    private static CartItem item(String id, int quantity, String price) {
        return new CartItem(id, "product-" + id, "store-1", quantity, new BigDecimal(price), "SKU-" + id,
                "azul", "M", ADDED, ADDED);
    }
}
