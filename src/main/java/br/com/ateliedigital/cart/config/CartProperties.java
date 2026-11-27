package br.com.ateliedigital.cart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Regras configuraveis do carrinho (prefixo {@code atelie.cart}).
 *
 * @param maxQuantityPerItem maximo de unidades de uma mesma variante no carrinho
 * @param maxDistinctItems   maximo de variantes diferentes no carrinho
 * @param keyPrefix          prefixo da chave do hash no Redis ({@code <prefixo><keycloakId>})
 */
@ConfigurationProperties(prefix = "atelie.cart")
public record CartProperties(
        @DefaultValue("99") int maxQuantityPerItem,
        @DefaultValue("50") int maxDistinctItems,
        @DefaultValue("cart:") String keyPrefix
) { }
