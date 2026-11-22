package br.com.ateliedigital.cart.integration.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

class CatalogHttpClientTest {
    private static final String BASE_URL = "http://catalog.test/api/v1/catalog";

    private MockRestServiceServer server;
    private CatalogHttpClient client;

    @BeforeEach
    void setUp() {
        bind(false);
    }

    @Test
    @DisplayName("Variant found is mapped to a snapshot (numeric ids read as text)")
    void lookupFound() {
        expectVariant("7", variantJson(7, "49.9", 3));

        VariantLookup lookup = client.lookup("7");

        assertThat(lookup).isInstanceOfSatisfying(VariantLookup.Found.class, found -> {
            ProductSnapshot product = found.product();
            assertThat(product.productVariantId()).isEqualTo("7");
            assertThat(product.productId()).isEqualTo("70");
            assertThat(product.storeId()).isEqualTo("700");
            assertThat(product.unitPrice()).isEqualByComparingTo("49.90");
            assertThat(product.stock()).isEqualTo(3);
            assertThat(product.color()).isEqualTo("azul");
        });
        server.verify();
    }

    @Test
    @DisplayName("Catalog 404 means the variant does not exist")
    void lookupNotFound() {
        server.expect(requestTo(BASE_URL + "/products/variations/7")).andRespond(withResourceNotFound());

        assertThat(client.lookup("7")).isInstanceOf(VariantLookup.Missing.class);
    }

    @Test
    @DisplayName("Catalog 5xx is a failure, not a missing product")
    void lookupServerError() {
        server.expect(requestTo(BASE_URL + "/products/variations/7")).andRespond(withServerError());

        assertThat(client.lookup("7")).isInstanceOf(VariantLookup.Failed.class);
    }

    @Test
    @DisplayName("Inactive or deleted variants are treated as missing")
    void lookupInactive() {
        expectVariant("7", """
                {"id": 7, "product_id": 70, "store_id": 700, "price": 10.0, "stock": 3, "is_active": false}""");

        assertThat(client.lookup("7")).isInstanceOf(VariantLookup.Missing.class);
    }

    @Test
    @DisplayName("Integrity: payload with another id, invalid price, missing store or negative stock is rejected")
    void lookupInconsistentData() {
        Map<String, String> payloads = Map.of(
                "1", variantJson(99, "10.0", 3),
                "2", variantJson(2, "0", 3),
                "3", """
                        {"id": 3, "product_id": 30, "price": 10.0, "stock": 3}""",
                "4", variantJson(4, "10.0", -1));
        List.of("1", "2", "3", "4").forEach(id -> expectVariant(id, payloads.get(id)));

        List.of("1", "2", "3", "4").forEach(id ->
                assertThat(client.lookup(id)).as("variant %s", id).isInstanceOf(VariantLookup.Missing.class));
    }

    @Test
    @DisplayName("Malformed body is a failure")
    void lookupMalformedBody() {
        expectVariant("7", "not json");

        assertThat(client.lookup("7")).isInstanceOf(VariantLookup.Failed.class);
    }

    @Test
    @DisplayName("Batch lookup keeps the order and isolates failures per variant")
    void lookupAll() {
        bind(true); // as consultas rodam em paralelo
        expectVariant("1", variantJson(1, "10.0", 1));
        server.expect(requestTo(BASE_URL + "/products/variations/2")).andRespond(withResourceNotFound());
        server.expect(requestTo(BASE_URL + "/products/variations/3"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        Map<String, VariantLookup> result = client.lookupAll(List.of("1", "2", "3", "1"));

        assertThat(result.keySet()).containsExactly("1", "2", "3");
        assertThat(result.get("1")).isInstanceOf(VariantLookup.Found.class);
        assertThat(result.get("2")).isInstanceOf(VariantLookup.Missing.class);
        assertThat(result.get("3")).isInstanceOf(VariantLookup.Failed.class);
        server.verify();
    }

    private void bind(boolean ignoreExpectOrder) {
        // Mesmo formato da API: o Jackson da aplicacao usa SNAKE_CASE globalmente.
        JsonMapper mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        RestClient.Builder builder = RestClient.builder()
                .configureMessageConverters(converters -> converters
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(mapper)));
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(ignoreExpectOrder).build();
        client = new CatalogHttpClient(builder, new CatalogProperties(BASE_URL));
    }

    private void expectVariant(String id, String body) {
        server.expect(requestTo(BASE_URL + "/products/variations/" + id))
                .andExpect(method(GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String variantJson(int id, String price, int stock) {
        return """
                {"id": %d, "product_id": %d, "store_id": %d, "price": %s, "stock": %d,
                 "weight": 0.5, "height": 10, "width": 10, "length": 10, "sku": "SKU-%d", "color": "azul", "size": null}"""
                .formatted(id, id * 10, id * 100, price, stock, id);
    }
}
