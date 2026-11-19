package br.com.ateliedigital.accounts.integration.keycloak;

import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KeycloakAdminClient {
    private static final String ARTISAN_ROLE = "ARTISAN";

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
        ResponseEntity<Void> response;
        try {
            response = restClient.post()
                    .uri("/admin/realms/{realm}/users", properties.realm())
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.Conflict exception) {
            throw new RuleConflit("Email is already registered");
        }
        String location = response.getHeaders().getLocation().toString();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    /**
     * Altera o e-mail (que tambem e o username do realm) e o marca como nao verificado.
     * Como o realm usa verifyEmail, o Keycloak exige e envia a verificacao no proximo login.
     */
    public void updateEmail(UUID keycloakId, String email) {
        try {
            restClient.put()
                    .uri("/admin/realms/{realm}/users/{id}", properties.realm(), keycloakId)
                    .headers(headers -> headers.setBearerAuth(adminToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", email, "emailVerified", false))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.Conflict exception) {
            throw new RuleConflit("Email is already registered");
        }
    }

    public void deleteUser(UUID keycloakId) {
        restClient.delete()
                .uri("/admin/realms/{realm}/users/{id}", properties.realm(), keycloakId)
                .headers(headers -> headers.setBearerAuth(adminToken()))
                .retrieve().toBodilessEntity();
    }

    /**
     * Envia o e-mail de cadastro (SMTP configurado no realm): verificacao do e-mail e definicao da senha.
     */
    public void sendAccountSetupEmail(UUID keycloakId) {
        executeActionsEmail(keycloakId, List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"));
    }

    private void executeActionsEmail(UUID keycloakId, List<String> actions) {
        restClient.put()
                .uri("/admin/realms/{realm}/users/{id}/execute-actions-email", properties.realm(), keycloakId)
                .headers(headers -> headers.setBearerAuth(adminToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(actions)
                .retrieve().toBodilessEntity();
    }

    public void grantArtisanRole(UUID keycloakId) {
        String token = adminToken();
        // A service account do realm possui apenas manage-users/view-users (sem view-realm),
        // por isso o papel e obtido pelos papeis disponiveis do proprio usuario.
        List<Map<String, Object>> availableRoles = restClient.get()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm/available", properties.realm(), keycloakId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve().body(new ParameterizedTypeReference<>() { });
        if (availableRoles == null) {
            throw new ResourceNotFoundException("Keycloak user was not found: " + keycloakId);
        }
        var role = availableRoles.stream()
                .filter(available -> ARTISAN_ROLE.equals(available.get("name")))
                .findFirst();
        if (role.isEmpty()) {
            return;
        }
        restClient.post()
                .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", properties.realm(), keycloakId)
                .headers(headers -> headers.setBearerAuth(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role.get()))
                .retrieve().toBodilessEntity();
    }

    private String adminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.rolesClientId());
        form.add("client_secret", properties.rolesClientSecret());
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
