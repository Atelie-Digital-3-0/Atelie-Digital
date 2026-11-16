package br.com.ateliedigital.accounts.integration.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "atelie.keycloak")
public record KeycloakProperties(String baseUrl, String realm, String rolesClientId, String rolesClientSecret) { }
