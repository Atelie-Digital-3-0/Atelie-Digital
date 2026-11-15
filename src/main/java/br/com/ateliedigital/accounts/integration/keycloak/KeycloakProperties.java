package br.com.ateliedigital.accounts.integration.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integrations.keycloak")
public record KeycloakProperties(String baseUrl, String realm, String clientId, String clientSecret) { }
