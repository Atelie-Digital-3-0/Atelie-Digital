package br.com.ateliedigital.cart.integration.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuracao do catalogo consultado pelo carrinho (prefixo {@code atelie.catalog}).
 *
 * @param baseUrl URL base da API do catalogo, incluindo o prefixo (ex.: {@code http://localhost:8008/api/v1/catalog})
 */
@ConfigurationProperties(prefix = "atelie.catalog")
public record CatalogProperties(@DefaultValue("http://localhost:8008/api/v1/catalog") String baseUrl) { }
