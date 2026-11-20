package br.com.ateliedigital.accounts.integration.service;

import br.com.ateliedigital.accounts.dto.AccountRequests.CompleteAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AddressResponses.AddressResponse;
import br.com.ateliedigital.accounts.service.AccountService;
import br.com.ateliedigital.accounts.service.AddressService;
import br.com.ateliedigital.exceptions.RuleConflit;
import br.com.ateliedigital.support.PostgresIntegrationTest;
import br.com.ateliedigital.support.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Roda os servicos contra um Postgres real: valida a constraint de endereco principal (indice unico parcial),
 * a ordem dos comandos SQL (o indice e checado a cada comando) e o complete idempotente.
 */
class AccountAddressServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired AccountService accountService;
    @Autowired AddressService addressService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void completeIsIdempotentAndDoesNotDuplicateAddresses() {
        JwtAuthenticationToken user = newUser();
        CompleteAccountRequest request = complete(address("01001000", "Rua A", "10", true));

        accountService.completeCurrent(user, request);
        accountService.completeCurrent(user, request);
        // mesmo local escrito de outra forma (caixa e espacos) tambem e reaproveitado
        AccountResponse response = accountService.completeCurrent(user,
                complete(address("01001000", "  rua a ", "10", true)));

        assertEquals(1, response.addresses().size());
        assertTrue(response.addresses().get(0).main());
    }

    @Test
    void completeWithNewMainAddressReplacesMainWithoutViolatingConstraint() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));

        AccountResponse response = accountService.completeCurrent(user,
                complete(address("02002000", "Rua B", "20", true)));

        assertEquals(2, response.addresses().size());
        assertEquals(List.of("Rua B"), mainStreets(response.addresses()));
    }

    @Test
    void completeWithExistingAddressAsMainPromotesItInsteadOfDuplicating() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));
        accountService.completeCurrent(user, complete(address("02002000", "Rua B", "20", true)));

        AccountResponse response = accountService.completeCurrent(user,
                complete(address("01001000", "Rua A", "10", true)));

        assertEquals(2, response.addresses().size());
        assertEquals(List.of("Rua A"), mainStreets(response.addresses()));
    }

    @Test
    void creatingAnEquivalentAddressIsRejectedAsConflict() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));

        // identico, e equivalente com outra caixa/espacos; main=true nao deve demover o principal atual
        assertThrows(RuleConflit.class, () -> addressService.create(user, address("01001000", "Rua A", "10", false)));
        assertThrows(RuleConflit.class, () -> addressService.create(user, address("01001000", " rua a ", "10", true)));

        assertEquals(1, addresses(user).size());
        assertEquals(List.of("Rua A"), mainStreets(addresses(user)));
        // numero diferente = outro endereco
        addressService.create(user, address("01001000", "Rua A", "11", false));
        assertEquals(2, addresses(user).size());
    }

    @Test
    void updatingAnAddressToMatchAnotherOneIsRejectedButKeepingItsOwnDataIsAllowed() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));
        addressService.create(user, address("02002000", "Rua B", "20", false));
        AddressResponse streetB = addresses(user).stream().filter(a -> a.street().equals("Rua B")).findFirst().orElseThrow();

        // igual ao endereco "Rua A" (outra caixa/espacos), sem tocar no principal
        assertThrows(RuleConflit.class,
                () -> addressService.update(user, streetB.id(), address("01001000", " RUA A", "10", true)));
        assertEquals(List.of("Rua A"), mainStreets(addresses(user)));

        // reenviar os proprios dados (ex.: so mudar o complemento) nao e conflito consigo mesmo
        AddressResponse updated = addressService.update(user, streetB.id(),
                new AddressRequest("02002000", "Rua B", "20", "Apto 5", "Centro", "Sao Paulo", "SP", false));
        assertEquals("Apto 5", updated.complement());
        assertEquals(2, addresses(user).size());
    }

    @Test
    void creatingOrUpdatingMainAddressKeepsSingleMain() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));

        addressService.create(user, address("02002000", "Rua B", "20", true));
        assertEquals(List.of("Rua B"), mainStreets(addresses(user)));

        AddressResponse streetA = addresses(user).stream().filter(a -> a.street().equals("Rua A")).findFirst().orElseThrow();
        addressService.update(user, streetA.id(), address("01001000", "Rua A", "10", true));
        assertEquals(List.of("Rua A"), mainStreets(addresses(user)));
    }

    @Test
    void deletingTheMainAddressPromotesAnotherOne() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));
        addressService.create(user, address("02002000", "Rua B", "20", false));
        addressService.create(user, address("03003000", "Rua C", "30", false));

        AddressResponse main = addresses(user).stream().filter(AddressResponse::main).findFirst().orElseThrow();
        addressService.delete(user, main.id());

        List<AddressResponse> remaining = addresses(user);
        assertEquals(2, remaining.size());
        assertEquals(1, mainStreets(remaining).size());
    }

    @Test
    void deletingANonMainAddressKeepsTheMain() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));
        addressService.create(user, address("02002000", "Rua B", "20", false));

        AddressResponse other = addresses(user).stream().filter(a -> !a.main()).findFirst().orElseThrow();
        addressService.delete(user, other.id());

        assertEquals(List.of("Rua A"), mainStreets(addresses(user)));
    }

    @Test
    void deletingTheOnlyAddressLeavesAccountWithoutAddresses() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));

        addressService.delete(user, addresses(user).get(0).id());

        assertTrue(addresses(user).isEmpty());
    }

    @Test
    void databaseRejectsTwoMainAddressesForTheSameAccount() {
        JwtAuthenticationToken user = newUser();
        accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));
        addressService.create(user, address("02002000", "Rua B", "20", false));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE accounts.addresses SET is_main = TRUE WHERE account_id = "
                        + "(SELECT id FROM accounts.accounts WHERE keycloak_id = ?::uuid)", user.getName()));
    }

    @Test
    void patchWithOnlyOneFieldKeepsTheOtherOnes() {
        JwtAuthenticationToken user = newUser();
        AccountResponse created = accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));

        AccountResponse patched = accountService.updateCurrent(user,
                new UpdateAccountRequest(null, null, null, null, null, "Nova bio"));

        assertEquals("Nova bio", patched.bio());
        assertEquals(created.firstName(), patched.firstName());
        assertEquals(created.lastName(), patched.lastName());
        assertEquals(created.cellphone(), patched.cellphone());
        assertEquals(created.dateOfBirth(), patched.dateOfBirth());
        assertEquals(created.email(), patched.email());
        assertEquals(1, patched.addresses().size());
    }

    @Test
    void patchWithEmptyPayloadChangesNothing() {
        JwtAuthenticationToken user = newUser();
        AccountResponse created = accountService.completeCurrent(user, complete(address("01001000", "Rua A", "10", true)));

        AccountResponse patched = accountService.updateCurrent(user, new UpdateAccountRequest(null, null, null, null, null, null));

        assertEquals(created, patched);
    }

    private List<AddressResponse> addresses(JwtAuthenticationToken user) {
        return addressService.list(user, PageRequest.of(0, 50)).getContent();
    }

    private static List<String> mainStreets(List<AddressResponse> addresses) {
        return addresses.stream().filter(AddressResponse::main).map(AddressResponse::street).toList();
    }

    private static AddressRequest address(String zip, String street, String number, boolean main) {
        return new AddressRequest(zip, street, number, null, "Centro", "Sao Paulo", "SP", main);
    }

    private static CompleteAccountRequest complete(AddressRequest address) {
        return new CompleteAccountRequest("Maria", "Silva", "11999999999", LocalDate.of(1990, 1, 1),
                currentCpf, null, address);
    }

    // Cada teste usa um usuario novo (token, e-mail e CPF unicos) para nao depender de ordem nem de limpeza.
    private static String currentCpf;

    private static JwtAuthenticationToken newUser() {
        TestUser user = TestUser.random();
        currentCpf = user.cpf();
        return user.token();
    }
}
