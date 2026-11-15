package br.com.ateliedigital.accounts.integration.keycloak;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KeycloakAdminClient {
    private final KeycloakProperties properties;
    private final RestClient restClient;

    public KeycloakAdminClient(KeycloakProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    public UUID createUser(String email, String firstName, String lastName) {
        String token = adminToken();
        Map<String, Object> user = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", false
        );
        var response = restClient.post()
                .uri("/admin/realms/{realm}/users", properties.realm())
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity();
        String location = response.getHeaders().getLocation().toString();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    public void sendAccountSetupEmail(UUID keycloakId) {
        restClient.put()
                .uri("/admin/realms/{realm}/users/{id}/execute-actions-email", properties.realm(), keycloakId)
                .headers(headers -> headers.setBearerAuth(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"))
                .retrieve().toBodilessEntity();
    }

    public void grantArtisanRole(UUID keycloakId) {
        String token = adminToken();
        Map<String, Object> user = restClient.get()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), keycloakId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(new ParameterizedTypeReference<>() { });
        if (user == null || user.get("id") == null) {
            throw new IllegalStateException("Keycloak user was not found: " + keycloakId);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> role = restClient.get()
                .uri("/admin/realms/{realm}/roles/ARTISAN", properties.realm())
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(new ParameterizedTypeReference<>() { });
        restClient.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), keycloakId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve().toBodilessEntity();
    }

//    public String mercadoPagoAccessToken(String userAccessToken) {
//        Map<String, Object> body = restClient.get()
//                .uri("/realms/{realm}/broker/mercado-pago/token", properties.realm())
//                .headers(headers -> headers.setBearerAuth(userAccessToken))
//                .retrieve().body(new ParameterizedTypeReference<>() { });
//        if (body == null || body.get("access_token") == null) {
//            throw new IllegalStateException("Mercado Pago access token is unavailable");
//        }
//        return body.get("access_token").toString();
//    }

    private String adminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        Map<String, Object> response = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(new ParameterizedTypeReference<>() { });
        if (response == null || response.get("access_token") == null) {
            throw new IllegalStateException("Could not obtain Keycloak admin token");
        }
        return response.get("access_token").toString();
    }
}
