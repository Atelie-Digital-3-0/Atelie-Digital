package br.com.ateliedigital.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis real (Testcontainers) compartilhado pelos testes do carrinho. Singleton: sobe uma vez por execucao.
 */
public final class RedisContainerSupport {
    public static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);

    static {
        REDIS.start();
    }

    private RedisContainerSupport() { }

    public static String host() {
        return REDIS.getHost();
    }

    public static int port() {
        return REDIS.getMappedPort(6379);
    }
}
