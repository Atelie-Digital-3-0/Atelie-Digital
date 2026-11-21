package br.com.ateliedigital.cart.repository;

import br.com.ateliedigital.cart.config.CartProperties;
import br.com.ateliedigital.cart.model.CartItem;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Carrinhos no Redis: um hash por usuario ({@code cart:{keycloakId}}), com um campo por variante de produto e o
 * {@link CartItem} serializado em JSON como valor. Os carrinhos nao expiram: saem do Redis apenas quando o usuario
 * os limpa (ou o checkout o fizer, via {@code CartManagement}).
 */
@Repository
public class CartRepository {
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

    /** Le o item, aplica {@code change} e grava o resultado. */
    public CartItem save(UUID userId, String productVariantId, ItemChange change) {
        String key = key(userId);
        CartItem updated = change.apply(hash().get(key, productVariantId), hash().size(key));
        hash().put(key, productVariantId, updated);
        return updated;
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
