package br.com.ateliedigital.cart.integration.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Adaptador HTTP da porta {@link ProductCatalog} para a atelie-catalog-api
 * ({@code GET {base-url}/products/variations/{id}}).
 * <p>
 * Alem de traduzir a resposta, valida a <b>integridade</b> dos dados recebidos: id igual ao solicitado, produto e
 * loja presentes, preco positivo e estoque nao negativo. Variantes marcadas como inativas/excluidas (quando o
 * catalogo enviar {@code is_active}/{@code is_deleted}) ou com dados inconsistentes sao tratadas como inexistentes.
 */
@Component
public class CatalogHttpClient implements ProductCatalog {
    private static final Logger log = LoggerFactory.getLogger(CatalogHttpClient.class);

    private final RestClient restClient;

    public CatalogHttpClient(RestClient.Builder builder, CatalogProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public VariantLookup lookup(String productVariantId) {
        try {
            VariantLookup lookup = restClient.get()
                    .uri("/products/variations/{id}", productVariantId)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                            return VariantLookup.missing();
                        }
                        if (response.getStatusCode().isError()) {
                            return VariantLookup.failed("catalog responded " + response.getStatusCode().value());
                        }
                        return toLookup(productVariantId, response.bodyTo(VariationPayload.class));
                    });
            if (lookup instanceof VariantLookup.Failed failed) {
                log.warn("Falha ao consultar a variante {} no catalogo: {}", productVariantId, failed.reason());
            }
            return lookup != null ? lookup : VariantLookup.failed("empty response");
        } catch (RuntimeException exception) {
            log.warn("Falha ao consultar a variante {} no catalogo: {}", productVariantId, exception.toString());
            return VariantLookup.failed(exception.getClass().getSimpleName());
        }
    }

    private static VariantLookup toLookup(String requestedId, VariationPayload payload) {
        if (payload == null) {
            return VariantLookup.failed("empty body");
        }
        if (Boolean.FALSE.equals(payload.isActive()) || Boolean.TRUE.equals(payload.isDeleted())) {
            return VariantLookup.missing();
        }
        String inconsistency = inconsistency(requestedId, payload);
        if (inconsistency != null) {
            log.warn("Variante {} ignorada: dados inconsistentes no catalogo ({})", requestedId, inconsistency);
            return VariantLookup.missing();
        }
        return VariantLookup.found(new ProductSnapshot(requestedId, payload.productId(), payload.storeId(),
                payload.price(), payload.stock(), payload.sku(), payload.color(), payload.size()));
    }

    private static String inconsistency(String requestedId, VariationPayload payload) {
        if (!requestedId.equals(payload.id())) return "id " + payload.id() + " differs from requested";
        if (payload.productId() == null) return "missing product_id";
        if (payload.storeId() == null) return "missing store_id";
        if (payload.price() == null || payload.price().signum() <= 0) return "invalid price " + payload.price();
        if (payload.stock() == null || payload.stock() < 0) return "invalid stock " + payload.stock();
        return null;
    }

    /**
     * Corpo de {@code ProductVariationDetail} da catalog-api (snake_case pela configuracao global do Jackson).
     * Ids numericos sao lidos como texto para o carrinho nao depender do tipo de chave do catalogo.
     */
    record VariationPayload(String id, String productId, String storeId, BigDecimal price, Integer stock,
                            String sku, String color, String size, Boolean isActive, Boolean isDeleted) { }
}
