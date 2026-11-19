package br.com.ateliedigital.accounts.integration.api;

import br.com.ateliedigital.accounts.dto.AddressResponses.ZipCodeResponse;
import br.com.ateliedigital.support.Payloads;
import br.com.ateliedigital.support.PostgresIntegrationTest;
import br.com.ateliedigital.support.TestUser;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo HTTP completo de enderecos: seguranca, regra do endereco principal, isolamento entre contas e Postgres real.
 */
class AddressApiIntegrationTest extends PostgresIntegrationTest {
    private static final String URL = "/api/accounts/me/addresses";

    // ----------------------------------------------------------------- seguranca

    @Test
    @DisplayName("Addresses not listed because request is anonymous")
    void listFailsWhenAnonymous() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Addresses not listed because registration is not completed")
    void listFailsWhenRegistrationIsNotCompleted() throws Exception {
        TestUser user = TestUser.random();

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("ACCOUNT_INCOMPLETE"));
    }

    @Test
    @DisplayName("Addresses not listed because user has no permission")
    void listFailsWhenRoleIsNotAllowed() throws Exception {
        TestUser user = completedUser();

        mockMvc.perform(get(URL).with(user.withRole("VISITOR")))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------- create

    @Test
    @DisplayName("Address created with success")
    void createAddress() throws Exception {
        TestUser user = completedUser();

        String body = mockMvc.perform(post(URL).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.address("02002000", "Rua B", "20", false)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.street").value("Rua B"))
                .andExpect(jsonPath("$.main").value(false))
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(body, "$.id");
        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_elements").value(2))
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')]", hasSize(1)));
    }

    @Test
    @DisplayName("Address created with success as main demoting the current main")
    void createMainAddressKeepsASingleMain() throws Exception {
        TestUser user = completedUser();

        mockMvc.perform(post(URL).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.address("02002000", "Rua B", "20", true)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(jsonPath("$.content[?(@.main == true)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.main == true)].street").value("Rua B"));
    }

    @Test
    @DisplayName("Address not created because address is registered")
    void createAddressFailsWhenAlreadyRegistered() throws Exception {
        TestUser user = completedUser();

        mockMvc.perform(post(URL).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.address("01001000", " rua a ", "10", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Address is already registered"));
    }

    @Test
    @DisplayName("Address not created because state is invalid")
    void createAddressFailsWhenBodyIsInvalid() throws Exception {
        TestUser user = completedUser();
        String invalid = Payloads.address("02002000", "Rua B", "20", false).replace("\"SP\"", "\"SAO\"");

        mockMvc.perform(post(URL).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------- list

    @Test
    @DisplayName("Addresses listed with success using pagination")
    void listAddressesPaginated() throws Exception {
        TestUser user = completedUser();
        createAddress(user, Payloads.address("02002000", "Rua B", "20", false));
        createAddress(user, Payloads.address("03003000", "Rua C", "30", false));

        mockMvc.perform(get(URL).with(user.client()).param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.total_elements").value(3))
                .andExpect(jsonPath("$.total_pages").value(2));

        mockMvc.perform(get(URL).with(user.client()).param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("Addresses listed only from the authenticated account")
    void listAddressesIsolatedByAccount() throws Exception {
        TestUser user = completedUser();
        TestUser other = completedUser();
        createAddress(other, Payloads.address("02002000", "Rua B", "20", false));

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(jsonPath("$.total_elements").value(1));
    }

    // -------------------------------------------------------------------- update

    @Test
    @DisplayName("Address updated with success")
    void updateAddress() throws Exception {
        TestUser user = completedUser();
        String id = createAddress(user, Payloads.address("02002000", "Rua B", "20", false));

        mockMvc.perform(put(URL + "/" + id).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.address("02002000", "Rua B Nova", "21", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.street").value("Rua B Nova"))
                .andExpect(jsonPath("$.number").value("21"));
    }

    @Test
    @DisplayName("Address updated with success as main demoting the current main")
    void updateAddressAsMain() throws Exception {
        TestUser user = completedUser();
        String id = createAddress(user, Payloads.address("02002000", "Rua B", "20", false));

        mockMvc.perform(put(URL + "/" + id).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.address("02002000", "Rua B", "20", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.main").value(true));

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(jsonPath("$.content[?(@.main == true)]", hasSize(1)))
                .andExpect(jsonPath("$.content[?(@.main == true)].id").value(id));
    }

    @Test
    @DisplayName("Address not updated because it matches another address")
    void updateAddressFailsWhenItMatchesAnotherAddress() throws Exception {
        TestUser user = completedUser();
        String id = createAddress(user, Payloads.address("02002000", "Rua B", "20", false));

        mockMvc.perform(put(URL + "/" + id).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.address("01001000", "Rua A", "10", false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Address is already registered"));
    }

    @Test
    @DisplayName("Address not updated because address belongs to another account")
    void updateAddressFailsWhenItBelongsToAnotherAccount() throws Exception {
        TestUser owner = completedUser();
        TestUser intruder = completedUser();
        String id = createAddress(owner, Payloads.address("02002000", "Rua B", "20", false));

        mockMvc.perform(put(URL + "/" + id).with(intruder.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.address("02002000", "Rua Invadida", "20", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Address not found"));
    }

    @Test
    @DisplayName("Address not updated because address does not exist")
    void updateAddressFailsWhenItDoesNotExist() throws Exception {
        TestUser user = completedUser();

        mockMvc.perform(put(URL + "/" + UUID.randomUUID()).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(Payloads.address("02002000", "Rua B", "20", false)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------- delete

    @Test
    @DisplayName("Address deleted with success returns 204")
    void deleteAddress() throws Exception {
        TestUser user = completedUser();
        String id = createAddress(user, Payloads.address("02002000", "Rua B", "20", false));

        mockMvc.perform(MockMvcRequestBuilders.delete(URL + "/" + id).with(user.client()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(jsonPath("$.total_elements").value(1))
                .andExpect(jsonPath("$.content[?(@.id == '" + id + "')]", hasSize(0)));
    }

    @Test
    @DisplayName("Address deleted with success promoting another address as main")
    void deleteMainAddressPromotesAnotherOne() throws Exception {
        TestUser user = completedUser();
        String second = createAddress(user, Payloads.address("02002000", "Rua B", "20", false));
        List<String> mainIds = JsonPath.read(mockMvc.perform(get(URL).with(user.client())).andReturn()
                .getResponse().getContentAsString(), "$.content[?(@.main == true)].id");
        String mainId = mainIds.get(0);

        mockMvc.perform(MockMvcRequestBuilders.delete(URL + "/" + mainId).with(user.client()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(URL).with(user.client()))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(second))
                .andExpect(jsonPath("$.content[0].main").value(true));
    }

    @Test
    @DisplayName("Address not deleted because address belongs to another account")
    void deleteAddressFailsWhenItBelongsToAnotherAccount() throws Exception {
        TestUser owner = completedUser();
        TestUser intruder = completedUser();
        String id = createAddress(owner, Payloads.address("02002000", "Rua B", "20", false));

        mockMvc.perform(MockMvcRequestBuilders.delete(URL + "/" + id).with(intruder.client()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(URL).with(owner.client()))
                .andExpect(jsonPath("$.total_elements").value(2));
    }

    // ----------------------------------------------------------------- lookupCep

    @Test
    @DisplayName("Zip code found with success before registration is completed")
    void lookupCepBeforeCompletingRegistration() throws Exception {
        TestUser user = TestUser.random();
        when(viaCepClient.lookup("01001000")).thenReturn(
                new ZipCodeResponse("01001-000", "Praca da Se", "", "Se", "Sao Paulo", "SP", true));

        mockMvc.perform(get(URL + "/cep/01001000").with(user.client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.street").value("Praca da Se"))
                .andExpect(jsonPath("$.zip_code").value("01001-000"));
    }

    // ------------------------------------------------------------------- helpers

    /** Usuario com cadastro concluido e um endereco principal ("Rua A", 01001000). */
    private TestUser completedUser() throws Exception {
        TestUser user = TestUser.random();
        completeRegistration(user, Payloads.address("01001000", "Rua A", "10", true)).andExpect(status().isOk());
        return user;
    }

    private String createAddress(TestUser user, String addressJson) throws Exception {
        String body = mockMvc.perform(post(URL).with(user.client())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }
}
