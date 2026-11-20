package br.com.ateliedigital.accounts.integration.api;

import br.com.ateliedigital.accounts.event.AccountCreatedEvent;
import br.com.ateliedigital.accounts.event.SensitiveAccountUpdatedEvent;
import br.com.ateliedigital.accounts.model.Account;
import br.com.ateliedigital.accounts.repository.AccountRepository;
import br.com.ateliedigital.support.Payloads;
import br.com.ateliedigital.support.PostgresIntegrationTest;
import br.com.ateliedigital.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo HTTP completo de contas: filtros de seguranca, validacao, controller, service e Postgres real.
 */
class AccountApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired AccountRepository accountRepository;

    // ---------------------------------------------------------- POST /accounts

    @Test
    @DisplayName("Account created with success without authentication")
    void createAccount() throws Exception {
        TestUser user = TestUser.random();
        when(keycloak.createUser(user.email(), "Maria", "Silva")).thenReturn(user.keycloakId());

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.createAccount(user)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.keycloak_id").value(user.keycloakId().toString()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.cpf").value(user.cpf()));

        assertTrue(accountRepository.existsByKeycloakId(user.keycloakId()));
        verify(keycloak).sendAccountSetupEmail(user.keycloakId());
        verify(auditPublisher).publishCreated(any(AccountCreatedEvent.class));
    }

    @Test
    @DisplayName("Account not created because CPF or Email is registered")
    void createAccountFailsWhenAlreadyRegistered() throws Exception {
        TestUser user = TestUser.random();
        when(keycloak.createUser(user.email(), "Maria", "Silva")).thenReturn(user.keycloakId());
        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.createAccount(user)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.createAccount(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("CPF or email is already registered"));

        verify(keycloak, times(1)).createUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Account not created because CPF is invalid")
    void createAccountFailsWhenCpfIsInvalid() throws Exception {
        TestUser user = new TestUser(UUID.randomUUID(), "invalid.cpf@teste.dev", "11111111111");

        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.createAccount(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("cpf must be a valid CPF with 11 digits"));

        verify(keycloak, never()).createUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Account not created and Keycloak user removed when sending setup email fails")
    void createAccountRollsBackWhenSetupEmailFails() throws Exception {
        TestUser user = TestUser.random();
        when(keycloak.createUser(user.email(), "Maria", "Silva")).thenReturn(user.keycloakId());
        doThrow(new IllegalStateException("smtp down")).when(keycloak).sendAccountSetupEmail(user.keycloakId());

        mockMvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.createAccount(user)))
                .andExpect(status().isInternalServerError());

        verify(keycloak).deleteUser(user.keycloakId());
        assertFalse(accountRepository.existsByKeycloakId(user.keycloakId()));
        assertFalse(accountRepository.existsByEmailIgnoreCase(user.email()));
    }

    // ------------------------------------------------------------ GET /me

    @Test
    @DisplayName("Current account not returned because request is anonymous")
    void getCurrentFailsWhenAnonymous() throws Exception {
        mockMvc.perform(get("/api/accounts/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Current account not returned because registration is not completed")
    void getCurrentFailsWhenRegistrationIsNotCompleted() throws Exception {
        TestUser user = TestUser.random();

        mockMvc.perform(get("/api/accounts/me").with(user.client()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    // ------------------------------------------------------- PUT /me/complete

    @Test
    @DisplayName("Registration completed with success creating account from token")
    void completeRegistrationCreatesAccountAndAddress() throws Exception {
        TestUser user = TestUser.random();

        completeRegistration(user, Payloads.address("01001000", "Rua A", "10", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keycloak_id").value(user.keycloakId().toString()))
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.addresses.length()").value(1))
                // primeiro endereco da conta sempre vira o principal
                .andExpect(jsonPath("$.addresses[0].main").value(true));

        mockMvc.perform(get("/api/accounts/me").with(user.client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpf").value(user.cpf()))
                .andExpect(jsonPath("$.date_of_birth").value("1990-05-20"))
                .andExpect(jsonPath("$.addresses.length()").value(1));
        verify(auditPublisher).publishCreated(any(AccountCreatedEvent.class));
    }

    @Test
    @DisplayName("Registration completed with success twice without duplicating address")
    void completeRegistrationIsIdempotent() throws Exception {
        TestUser user = TestUser.random();
        String address = Payloads.address("01001000", "Rua A", "10", true);

        completeRegistration(user, address).andExpect(status().isOk());
        completeRegistration(user, address)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(1));
    }

    @Test
    @DisplayName("Registration not completed because CPF belongs to another account")
    void completeRegistrationFailsWhenCpfBelongsToAnotherAccount() throws Exception {
        TestUser owner = TestUser.random();
        completeRegistration(owner, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());
        TestUser other = new TestUser(UUID.randomUUID(), "other." + UUID.randomUUID() + "@teste.dev", owner.cpf());

        completeRegistration(other, Payloads.address("01001000", "Rua A", "10", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("CPF is already registered"));

        assertFalse(accountRepository.existsByKeycloakId(other.keycloakId()));
    }

    @Test
    @DisplayName("Registration not completed because address is missing")
    void completeRegistrationFailsWhenAddressIsMissing() throws Exception {
        TestUser user = TestUser.random();

        completeRegistration(user, "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Campo obrigatório"));

        assertFalse(accountRepository.existsByKeycloakId(user.keycloakId()));
    }

    // ------------------------------------------------------------ PATCH /me

    @Test
    @DisplayName("Account not updated because registration is not completed")
    void updateCurrentFailsWhenRegistrationIsNotCompleted() throws Exception {
        TestUser user = TestUser.random();

        mockMvc.perform(patch("/api/accounts/me").with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Nova bio\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACCOUNT_INCOMPLETE"));
    }

    @Test
    @DisplayName("Account not updated because user has no permission")
    void updateCurrentFailsWhenRoleIsNotAllowed() throws Exception {
        TestUser user = TestUser.random();
        completeRegistration(user, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/accounts/me").with(user.withRole("VISITOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Nova bio\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Account updated with success keeping the other fields")
    void updateCurrentPartially() throws Exception {
        TestUser user = TestUser.random();
        completeRegistration(user, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/accounts/me").with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"first_name\": \"Ana\", \"bio\": \"Nova bio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.first_name").value("Ana"))
                .andExpect(jsonPath("$.bio").value("Nova bio"))
                .andExpect(jsonPath("$.last_name").value("Silva"))
                .andExpect(jsonPath("$.email").value(user.email()));

        mockMvc.perform(get("/api/accounts/me").with(user.client()))
                .andExpect(jsonPath("$.first_name").value("Ana"))
                .andExpect(jsonPath("$.addresses.length()").value(1));
        verify(keycloak, never()).updateEmail(any(), anyString());
    }

    @Test
    @DisplayName("Account updated with success changing Email and syncing Keycloak")
    void updateCurrentChangingEmail() throws Exception {
        TestUser user = TestUser.random();
        String newEmail = "new." + UUID.randomUUID() + "@teste.dev";
        completeRegistration(user, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/accounts/me").with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + newEmail + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));

        verify(keycloak).updateEmail(user.keycloakId(), newEmail);
        verify(auditPublisher).publishSensitiveUpdated(any(SensitiveAccountUpdatedEvent.class));
        assertEquals(newEmail, accountRepository.findByKeycloakId(user.keycloakId()).orElseThrow().getEmail());
    }

    @Test
    @DisplayName("Account not updated because Email is registered")
    void updateCurrentFailsWhenEmailIsAlreadyRegistered() throws Exception {
        TestUser owner = TestUser.random();
        TestUser user = TestUser.random();
        completeRegistration(owner, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());
        completeRegistration(user, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/accounts/me").with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + owner.email().toUpperCase() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already registered"));

        verify(keycloak, never()).updateEmail(any(), anyString());
    }

    @Test
    @DisplayName("Account not updated by Keycloak failure keeping the old Email")
    void updateCurrentRollsBackWhenKeycloakFails() throws Exception {
        TestUser user = TestUser.random();
        String newEmail = "new." + UUID.randomUUID() + "@teste.dev";
        completeRegistration(user, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());
        doThrow(new IllegalStateException("keycloak down")).when(keycloak).updateEmail(user.keycloakId(), newEmail);

        mockMvc.perform(patch("/api/accounts/me").with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + newEmail + "\"}"))
                .andExpect(status().isInternalServerError());

        Account stored = accountRepository.findByKeycloakId(user.keycloakId()).orElseThrow();
        assertEquals(user.email(), stored.getEmail());
    }
}
