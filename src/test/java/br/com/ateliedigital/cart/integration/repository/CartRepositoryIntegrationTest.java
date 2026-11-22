package br.com.ateliedigital.cart.integration.repository;

import br.com.ateliedigital.cart.config.CartConfiguration;
import br.com.ateliedigital.cart.config.CartProperties;
import br.com.ateliedigital.cart.model.CartItem;
import br.com.ateliedigital.cart.repository.CartRepository;
import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.support.RedisContainerSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Repositorio contra um Redis real: serializacao JSON, ausencia de TTL e atomicidade das escritas concorrentes.
 */
class CartRepositoryIntegrationTest {
    private static LettuceConnectionFactory connectionFactory;
    private static CartRepository repository;
    private static StringRedisTemplate rawRedis;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(RedisContainerSupport.host(), RedisContainerSupport.port()));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        RedisTemplate<String, CartItem> template = new CartConfiguration().cartRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        repository = new CartRepository(template, new CartProperties(99, 50, "cart:"));

        rawRedis = new StringRedisTemplate(connectionFactory);
        rawRedis.afterPropertiesSet();
    }

    @AfterAll
    static void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("Item is stored as JSON in the user's hash, without expiration")
    void itemIsSerializedAsJsonWithoutTtl() {
        UUID user = UUID.randomUUID();
        Instant now = Instant.now();

        repository.save(user, "7", (current, count) -> item("7", 2, now));

        String key = "cart:" + user;
        Object raw = rawRedis.opsForHash().get(key, "7");
        assertThat(raw).asString()
                .contains("\"productVariantId\":\"7\"")
                .contains("\"quantity\":2")
                .contains("\"unitPrice\":49.90");
        assertThat(rawRedis.getExpire(key)).isEqualTo(-1L); // -1 = chave sem TTL
        // Ida e volta pelo JSON preserva todos os campos (inclusive BigDecimal e Instant com nanos).
        assertThat(repository.find(user, "7")).contains(item("7", 2, now));
    }

    @Test
    @DisplayName("Items are listed in insertion order and carts are isolated per user")
    void findAllIsOrderedAndIsolated() {
        UUID user = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Instant now = Instant.now();
        repository.save(user, "b", (current, count) -> item("b", 1, now.plusSeconds(1)));
        repository.save(user, "a", (current, count) -> item("a", 1, now.plusSeconds(2)));
        repository.save(other, "z", (current, count) -> item("z", 1, now));

        assertThat(repository.findAll(user)).extracting(CartItem::productVariantId).containsExactly("b", "a");
        assertThat(repository.findAll(other)).extracting(CartItem::productVariantId).containsExactly("z");
    }

    @Test
    @DisplayName("Change receives the current item and the number of items; a rejected change writes nothing")
    void rejectedChangeWritesNothing() {
        UUID user = UUID.randomUUID();
        repository.save(user, "1", (current, count) -> item("1", 1, Instant.now()));

        assertThatThrownBy(() -> repository.save(user, "2", (current, count) -> {
            assertThat(current).isNull();
            assertThat(count).isEqualTo(1);
            throw new VioletedRule("limit");
        })).isInstanceOf(VioletedRule.class);

        assertThat(repository.exists(user, "2")).isFalse();
        assertThat(repository.findAll(user)).hasSize(1);
    }

    @Test
    @DisplayName("Concurrent increments of the same item are not lost (WATCH/MULTI/EXEC)")
    void concurrentIncrementsAreAtomic() throws Exception {
        UUID user = UUID.randomUUID();
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return repository.save(user, "7", (current, count) -> current == null
                            ? item("7", 1, Instant.now())
                            : new CartItem("7", "70", "700", current.quantity() + 1, current.unitPrice(), null,
                            null, null, current.addedAt(), Instant.now()));
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        }

        assertThat(repository.find(user, "7")).get().extracting(CartItem::quantity).isEqualTo(threads);
    }

    @Test
    @DisplayName("Remove, remove many and clear")
    void removeAndClear() {
        UUID user = UUID.randomUUID();
        Instant now = Instant.now();
        List.of("1", "2", "3", "4").forEach(id -> repository.save(user, id, (current, count) -> item(id, 1, now)));

        assertThat(repository.remove(user, "1")).isTrue();
        assertThat(repository.remove(user, "1")).isFalse();
        repository.removeAll(user, List.of("2", "3"));
        assertThat(repository.findAll(user)).extracting(CartItem::productVariantId).containsExactly("4");

        repository.clear(user);
        assertThat(repository.findAll(user)).isEmpty();
        assertThat(rawRedis.hasKey("cart:" + user)).isFalse();
    }

    private static CartItem item(String id, int quantity, Instant addedAt) {
        return new CartItem(id, "70", "700", quantity, new BigDecimal("49.90"), "SKU", "azul", "M", addedAt, addedAt);
    }
}
