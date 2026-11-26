package br.com.ateliedigital.cart.repository;

import br.com.ateliedigital.cart.config.CartProperties;
import br.com.ateliedigital.cart.model.CartItem;
import br.com.ateliedigital.exceptions.RuleConflit;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Carrinhos no Redis: um hash por usuario ({@code cart:{keycloakId}}), com um campo por variante de produto e o
 * {@link CartItem} serializado em JSON como valor. Os carrinhos nao expiram: saem do Redis apenas quando o usuario
 * os limpa (ou o checkout o fizer, via {@code CartManagement}).
 * <p>
 * Escritas que dependem do estado atual (somar quantidade, limite de itens) usam {@code WATCH/MULTI/EXEC}: se o
 * carrinho mudar entre a leitura e a gravacao (outra aba/dispositivo), a operacao e refeita sobre o estado novo.
 */
@Repository
public class CartRepository {
    private static final int MAX_ATTEMPTS = 10;
    private static final Comparator<CartItem> BY_ADDED_AT = Comparator.comparing(CartItem::addedAt,
            Comparator.nullsLast(Comparator.naturalOrder()));

    private final RedisTemplate<String, CartItem> redis;
    private final String keyPrefix;

    public CartRepository(RedisTemplate<String, CartItem> cartRedisTemplate, CartProperties properties) {
        this.redis = cartRedisTemplate;
        this.keyPrefix = properties.keyPrefix();
    }

    /**
     * Calcula o novo estado de um item a partir do estado atual. Pode lancar excecao para recusar a alteracao
     * (nada e gravado nesse caso).
     */
    @FunctionalInterface
    public interface ItemChange {
        /**
         * @param current   item atualmente no carrinho, ou {@code null} se a variante ainda nao esta nele
         * @param itemCount quantidade de variantes diferentes no carrinho
         * @return o item a ser gravado
         */
        CartItem apply(CartItem current, long itemCount);
    }

    /** Itens do carrinho na ordem em que foram adicionados. */
    public List<CartItem> findAll(UUID userId) {
        return hash().values(key(userId)).stream()
                .filter(Objects::nonNull)
                .sorted(BY_ADDED_AT)
                .toList();
    }

    public Optional<CartItem> find(UUID userId, String productVariantId) {
        return Optional.ofNullable(hash().get(key(userId), productVariantId));
    }

    public boolean exists(UUID userId, String productVariantId) {
        return hash().hasKey(key(userId), productVariantId);
    }

    /**
     * Le o item, aplica {@code change} e grava o resultado de forma atomica (otimista).
     *
     * @throws RuleConflit se o carrinho continuar mudando concorrentemente apos {@value #MAX_ATTEMPTS} tentativas
     */
    public CartItem save(UUID userId, String productVariantId, ItemChange change) {
        String key = key(userId);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            CartItem saved = redis.execute(new SessionCallback<>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> CartItem execute(RedisOperations<K, V> operations) {
                    RedisOperations<String, CartItem> ops = (RedisOperations<String, CartItem>) operations;
                    HashOperations<String, String, CartItem> hash = ops.opsForHash();

                    ops.watch(key);
                    CartItem updated;
                    try {
                        updated = change.apply(hash.get(key, productVariantId), hash.size(key));
                    } catch (RuntimeException rejected) {
                        ops.unwatch();
                        throw rejected;
                    }
                    ops.multi();
                    hash.put(key, productVariantId, updated);
                    List<Object> results = ops.exec();
                    // Lista vazia: o WATCH detectou alteracao concorrente e a transacao foi descartada.
                    return results == null || results.isEmpty() ? null : updated;
                }
            });
            if (saved != null) {
                return saved;
            }
            backoff(attempt);
        }
        throw new RuleConflit("Cart was modified concurrently, please try again");
    }

    /** Espera curta e aleatoria antes de refazer, para requisicoes concorrentes nao colidirem de novo em sequencia. */
    private static void backoff(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, 10L * (attempt + 1)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuleConflit("Cart was modified concurrently, please try again");
        }
    }

    /** @return {@code true} se o item existia */
    public boolean remove(UUID userId, String productVariantId) {
        return hash().delete(key(userId), productVariantId) > 0;
    }

    public void removeAll(UUID userId, Collection<String> productVariantIds) {
        if (!productVariantIds.isEmpty()) {
            hash().delete(key(userId), productVariantIds.toArray());
        }
    }

    public void clear(UUID userId) {
        redis.delete(key(userId));
    }

    private HashOperations<String, String, CartItem> hash() {
        return redis.opsForHash();
    }

    private String key(UUID userId) {
        return keyPrefix + userId;
    }
}
