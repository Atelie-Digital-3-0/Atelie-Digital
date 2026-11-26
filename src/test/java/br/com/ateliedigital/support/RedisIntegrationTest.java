package br.com.ateliedigital.support;

import br.com.ateliedigital.cart.integration.catalog.ProductCatalog;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Base dos testes de integracao do carrinho: Postgres (contas) e Redis (carrinhos) reais. O catalogo, servico
 * externo, e mockado.
 */
public abstract class RedisIntegrationTest extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", RedisContainerSupport::host);
        registry.add("spring.data.redis.port", RedisContainerSupport::port);
        registry.add("spring.data.redis.password", () -> "");
    }

    @MockitoBean protected ProductCatalog productCatalog;
}
