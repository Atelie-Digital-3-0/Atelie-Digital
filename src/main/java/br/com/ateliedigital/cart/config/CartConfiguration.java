package br.com.ateliedigital.cart.config;

import br.com.ateliedigital.cart.model.CartItem;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(CartProperties.class)
public class CartConfiguration {

    /**
     * Template dedicado aos carrinhos: chave e campos do hash em texto, valor ({@link CartItem}) serializado em JSON.
     * <p>
     * O {@link JsonMapper} e proprio (e nao o da camada web) para o formato gravado no Redis nao mudar se a
     * configuracao da API mudar. Campos desconhecidos sao ignorados para que versoes antigas/novas do
     * {@link CartItem} continuem legiveis.
     */
    @Bean
    public RedisTemplate<String, CartItem> cartRedisTemplate(RedisConnectionFactory connectionFactory) {
        JsonMapper storageMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        RedisSerializer<CartItem> itemSerializer = new JacksonJsonRedisSerializer<>(storageMapper, CartItem.class);

        RedisTemplate<String, CartItem> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setValueSerializer(itemSerializer);
        template.setHashValueSerializer(itemSerializer);
        return template;
    }
}
