package br.com.ateliedigital.cart.config;

import br.com.ateliedigital.cart.model.CartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Formato gravado no Redis, sem precisar de um Redis: o template e montado com uma conexao mockada e os
 * serializadores sao exercitados diretamente.
 */
class CartConfigurationTest {
    private final RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
    private RedisTemplate<String, CartItem> template;

    @BeforeEach
    void setUp() {
        template = new CartConfiguration().cartRedisTemplate(connectionFactory);
    }

    @Test
    @DisplayName("Chaves e campos do hash sao texto puro; o template usa a conexao informada")
    void keysAreStrings() {
        assertThat(template.getConnectionFactory()).isSameAs(connectionFactory);
        RedisSerializer<?> keySerializer = template.getKeySerializer();
        RedisSerializer<?> hashKeySerializer = template.getHashKeySerializer();

        assertThat(serialize(keySerializer, "cart:123")).isEqualTo("cart:123");
        assertThat(serialize(hashKeySerializer, "101")).isEqualTo("101");
    }

    @Test
    @DisplayName("O item e gravado em JSON e lido de volta sem perda (valor do hash e valor simples)")
    void itemRoundTrip() {
        CartItem item = new CartItem("101", "10", "1", 2, new BigDecimal("49.90"), "CAN-AZUL-M", "azul", "M",
                Instant.parse("2026-11-19T10:00:00Z"), Instant.parse("2026-11-20T12:00:00Z"));

        for (RedisSerializer<?> serializer : new RedisSerializer<?>[]{
                template.getHashValueSerializer(), template.getValueSerializer()}) {
            @SuppressWarnings("unchecked")
            RedisSerializer<CartItem> itemSerializer = (RedisSerializer<CartItem>) serializer;
            byte[] json = itemSerializer.serialize(item);

            assertThat(new String(json, StandardCharsets.UTF_8))
                    .contains("\"productVariantId\":\"101\"")
                    .contains("\"quantity\":2");
            assertThat(itemSerializer.deserialize(json)).isEqualTo(item);
        }
    }

    @Test
    @DisplayName("Campos desconhecidos (versao mais nova do CartItem) sao ignorados na leitura")
    void unknownFieldsAreIgnored() {
        @SuppressWarnings("unchecked")
        RedisSerializer<CartItem> serializer = (RedisSerializer<CartItem>) template.getHashValueSerializer();
        String json = """
                {"productVariantId":"101","productId":"10","storeId":"1","quantity":2,"unitPrice":49.90,
                 "sku":"CAN-AZUL-M","color":"azul","size":"M","giftWrap":true}""";

        CartItem item = serializer.deserialize(json.getBytes(StandardCharsets.UTF_8));

        assertThat(item.productVariantId()).isEqualTo("101");
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.addedAt()).isNull();
    }

    @SuppressWarnings("unchecked")
    private static String serialize(RedisSerializer<?> serializer, String value) {
        return new String(((RedisSerializer<String>) serializer).serialize(value), StandardCharsets.UTF_8);
    }
}
