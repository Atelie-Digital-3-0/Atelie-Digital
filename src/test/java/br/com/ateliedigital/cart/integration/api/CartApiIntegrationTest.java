package br.com.ateliedigital.cart.integration.api;

import br.com.ateliedigital.cart.integration.catalog.ProductSnapshot;
import br.com.ateliedigital.cart.integration.catalog.VariantLookup;
import br.com.ateliedigital.support.Payloads;
import br.com.ateliedigital.support.RedisIntegrationTest;
import br.com.ateliedigital.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo HTTP completo do carrinho: seguranca (JWT + conta completa + papeis), Redis real, isolamento entre
 * usuarios e reconciliacao com o catalogo (mockado).
 */
class CartApiIntegrationTest extends RedisIntegrationTest {
    private static final String URL = "/api/carts";

    /** Catalogo simulado: id da variante -> resultado da consulta (ausente = variante inexistente). */
    private final Map<String, VariantLookup> catalog = new ConcurrentHashMap<>();

    @BeforeEach
    void stubCatalog() {
        when(productCatalog.lookup(anyString()))
                .thenAnswer(invocation -> catalog.getOrDefault(invocation.<String>getArgument(0), VariantLookup.missing()));
        when(productCatalog.lookupAll(anyCollection())).thenAnswer(invocation -> {
            Map<String, VariantLookup> result = new LinkedHashMap<>();
            invocation.<Collection<String>>getArgument(0)
                    .forEach(id -> result.put(id, catalog.getOrDefault(id, VariantLookup.missing())));
            return result;
        });
    }

    // ----------------------------------------------------------------- seguranca

    @Test
    @DisplayName("Cart not returned because request is anonymous")
    void getFailsWhenAnonymous() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cart not returned because registration is not completed")
    void getFailsWhenRegistrationIsNotCompleted() throws Exception {
        mockMvc.perform(get(URL).with(TestUser.random().client()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACCOUNT_INCOMPLETE"));
    }

    @Test
    @DisplayName("Cart not returned because the role is not allowed")
    void getFailsWhenRoleIsNotAllowed() throws Exception {
        TestUser user = completedUser();

        mockMvc.perform(get(URL).with(user.withRole("VISITOR"))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(user.withRole("ADMIN"))).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ fluxo

    @Test
    @DisplayName("Full flow: add, sum, update, remove and clear")
    void fullFlow() throws Exception {
        TestUser user = completedUser();
        catalog.put("10", product("10", "49.90", 10));
        catalog.put("20", product("20", "15.00", 5));

        addItem(user, "10", 2).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        addItem(user, "10", 1).andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value(3));
        addItem(user, "20", 1).andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].product_variant_id").value("10"))
                .andExpect(jsonPath("$.items[0].subtotal").value(149.70))
                .andExpect(jsonPath("$.total_items").value(2))
                .andExpect(jsonPath("$.total_quantity").value(4))
                .andExpect(jsonPath("$.total_price").value(164.70))
                .andExpect(jsonPath("$.verified").value(true));

        mockMvc.perform(patch(URL + "/items/10").with(user.client())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(1));

        mockMvc.perform(delete(URL + "/items/20").with(user.client())).andExpect(status().isNoContent());
        mockMvc.perform(delete(URL + "/items/20").with(user.client())).andExpect(status().isNotFound());

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total_price").value(49.90));

        mockMvc.perform(delete(URL).with(user.client())).andExpect(status().isNoContent());
        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total_price").value(0));
    }

    @Test
    @DisplayName("Artisans can use the cart and carts are isolated per user")
    void cartsAreIsolatedPerUser() throws Exception {
        TestUser artisan = completedUser();
        TestUser client = completedUser();
        catalog.put("10", product("10", "49.90", 10));

        mockMvc.perform(post(URL + "/items").with(artisan.withRole("ARTISAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_variant_id\": \"10\", \"quantity\": 1}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(client.client())).andExpect(jsonPath("$.items", hasSize(0)));
        mockMvc.perform(patch(URL + "/items/10").with(client.client())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 2}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(URL).with(artisan.withRole("ARTISAN"))).andExpect(jsonPath("$.items", hasSize(1)));
    }

    // ------------------------------------------------------------ integridade

    @Test
    @DisplayName("Item not added because the product does not exist, stock is insufficient or catalog is down")
    void addValidatesProductIntegrity() throws Exception {
        TestUser user = completedUser();
        catalog.put("10", product("10", "49.90", 2));
        catalog.put("30", VariantLookup.failed("timeout"));

        addItem(user, "99", 1).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));
        addItem(user, "10", 3).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock: only 2 units available"));
        addItem(user, "10", 100).andExpect(status().isConflict());
        addItem(user, "30", 1).andExpect(status().isServiceUnavailable());

        mockMvc.perform(get(URL).with(user.client())).andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    @DisplayName("Reading the cart flags price/stock changes and removes products that left the catalog")
    void getReconcilesWithCatalog() throws Exception {
        TestUser user = completedUser();
        catalog.put("10", product("10", "49.90", 10));
        catalog.put("20", product("20", "15.00", 5));
        catalog.put("30", product("30", "8.00", 5));
        addItem(user, "10", 2).andExpect(status().isCreated());
        addItem(user, "20", 3).andExpect(status().isCreated());
        addItem(user, "30", 1).andExpect(status().isCreated());

        catalog.put("10", product("10", "59.90", 10)); // preco subiu
        catalog.put("20", product("20", "15.00", 1));  // estoque caiu
        catalog.remove("30");                          // produto excluido

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].status").value("PRICE_CHANGED"))
                .andExpect(jsonPath("$.items[0].unit_price").value(59.90))
                .andExpect(jsonPath("$.items[0].previous_unit_price").value(49.90))
                .andExpect(jsonPath("$.items[1].status").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.items[1].available_stock").value(1))
                .andExpect(jsonPath("$.total_price").value(119.80))
                .andExpect(jsonPath("$.removed_items[0].product_variant_id").value("30"))
                .andExpect(jsonPath("$.removed_items[0].reason").value("PRODUCT_UNAVAILABLE"));

        // O item removido saiu do Redis; atualizar a quantidade "aceita" o novo preco.
        mockMvc.perform(patch(URL + "/items/10").with(user.client())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\": 2}"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.removed_items", hasSize(0)));
    }

    @Test
    @DisplayName("Cart is still returned, unverified, when the catalog is down")
    void getWhenCatalogIsDown() throws Exception {
        TestUser user = completedUser();
        catalog.put("10", product("10", "49.90", 10));
        addItem(user, "10", 2).andExpect(status().isCreated());

        catalog.put("10", VariantLookup.failed("timeout"));

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.items[0].status").value("UNVERIFIED"))
                .andExpect(jsonPath("$.items[0].unit_price").value(49.90))
                .andExpect(jsonPath("$.total_price").value(99.80));
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions addItem(TestUser user, String variantId, int quantity) throws Exception {
        return mockMvc.perform(post(URL + "/items").with(user.client())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"product_variant_id\": \"%s\", \"quantity\": %d}".formatted(variantId, quantity)));
    }

    private TestUser completedUser() throws Exception {
        TestUser user = TestUser.random();
        completeRegistration(user, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());
        return user;
    }

    private static VariantLookup product(String id, String price, int stock) {
        return VariantLookup.found(new ProductSnapshot(id, "p" + id, "s1", new BigDecimal(price), stock,
                "SKU-" + id, null, null));
    }
}
