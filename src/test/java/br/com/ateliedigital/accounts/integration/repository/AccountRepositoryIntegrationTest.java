package br.com.ateliedigital.accounts.integration.repository;

import br.com.ateliedigital.accounts.model.Account;
import br.com.ateliedigital.accounts.model.Address;
import br.com.ateliedigital.accounts.repository.AccountRepository;
import br.com.ateliedigital.accounts.repository.AddressRepository;
import br.com.ateliedigital.support.PostgresIntegrationTest;
import br.com.ateliedigital.support.TestUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Consultas derivadas e constraints de {@link AccountRepository} validadas contra o Postgres real (migrations do Flyway).
 */
class AccountRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired AccountRepository accountRepository;
    @Autowired AddressRepository addressRepository;

    @Test
    @DisplayName("Account saved with success generating its id")
    void saveAccount() {
        TestUser user = TestUser.random();

        Account saved = accountRepository.saveAndFlush(account(user));

        assertNotNull(saved.getId());
        Account found = accountRepository.findById(saved.getId()).orElseThrow();
        assertEquals(user.email(), found.getEmail());
        assertEquals(user.cpf(), found.getCpf());
        assertEquals(LocalDate.of(1990, 5, 20), found.getDateOfBirth());
        assertNull(found.getBio());
    }

    @Test
    @DisplayName("Account found by Keycloak id")
    void findByKeycloakId() {
        TestUser user = TestUser.random();
        Account saved = accountRepository.saveAndFlush(account(user));

        assertEquals(saved.getId(), accountRepository.findByKeycloakId(user.keycloakId()).orElseThrow().getId());
        assertTrue(accountRepository.existsByKeycloakId(user.keycloakId()));
    }

    @Test
    @DisplayName("Account not found because Keycloak id does not exist")
    void findByKeycloakIdWhenMissing() {
        UUID unknown = UUID.randomUUID();

        assertTrue(accountRepository.findByKeycloakId(unknown).isEmpty());
        assertFalse(accountRepository.existsByKeycloakId(unknown));
    }

    @Test
    @DisplayName("Account CPF checked with success")
    void existsByCpf() {
        TestUser user = TestUser.random();
        accountRepository.saveAndFlush(account(user));

        assertTrue(accountRepository.existsByCpf(user.cpf()));
        assertFalse(accountRepository.existsByCpf(TestUser.randomValidCpf()));
    }

    @Test
    @DisplayName("Account Email checked with success ignoring case")
    void existsByEmailIgnoreCase() {
        TestUser user = TestUser.random();
        accountRepository.saveAndFlush(account(user));

        assertTrue(accountRepository.existsByEmailIgnoreCase(user.email().toUpperCase()));
        assertFalse(accountRepository.existsByEmailIgnoreCase("nobody." + UUID.randomUUID() + "@teste.dev"));
    }

    @Test
    @DisplayName("Account not saved because CPF is registered")
    void saveFailsWhenCpfIsDuplicated() {
        TestUser user = TestUser.random();
        accountRepository.saveAndFlush(account(user));
        TestUser other = new TestUser(UUID.randomUUID(), "other." + UUID.randomUUID() + "@teste.dev", user.cpf());

        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.saveAndFlush(account(other)));
    }

    @Test
    @DisplayName("Account not saved because Email is registered")
    void saveFailsWhenEmailIsDuplicated() {
        TestUser user = TestUser.random();
        accountRepository.saveAndFlush(account(user));
        TestUser other = new TestUser(UUID.randomUUID(), user.email(), TestUser.randomValidCpf());

        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.saveAndFlush(account(other)));
    }

    @Test
    @DisplayName("Account not saved because Keycloak id is registered")
    void saveFailsWhenKeycloakIdIsDuplicated() {
        TestUser user = TestUser.random();
        accountRepository.saveAndFlush(account(user));
        TestUser other = new TestUser(user.keycloakId(), "other." + UUID.randomUUID() + "@teste.dev",
                TestUser.randomValidCpf());

        assertThrows(DataIntegrityViolationException.class, () -> accountRepository.saveAndFlush(account(other)));
    }

    @Test
    @DisplayName("Account saved with success persisting its addresses by cascade")
    void saveAccountCascadesAddresses() {
        Account account = account(TestUser.random());
        account.addAddress(address("Rua A", true));
        account.addAddress(address("Rua B", false));

        Account saved = accountRepository.saveAndFlush(account);

        assertEquals(2, addressRepository.findAllByAccountId(saved.getId()).size());
    }

    @Test
    @DisplayName("Account deleted with success removing its addresses")
    void deleteAccountRemovesAddresses() {
        Account account = account(TestUser.random());
        account.addAddress(address("Rua A", true));
        Account saved = accountRepository.saveAndFlush(account);

        accountRepository.deleteById(saved.getId());

        assertTrue(accountRepository.findById(saved.getId()).isEmpty());
        assertTrue(addressRepository.findAllByAccountId(saved.getId()).isEmpty());
    }

    static Account account(TestUser user) {
        Account account = new Account();
        account.setKeycloakId(user.keycloakId());
        account.setFirstName("Maria");
        account.setLastName("Silva");
        account.setCellphone("11999999999");
        account.setDateOfBirth(LocalDate.of(1990, 5, 20));
        account.setCpf(user.cpf());
        account.setEmail(user.email());
        return account;
    }

    static Address address(String street, boolean main) {
        Address address = new Address();
        address.setZipCode("01001000");
        address.setStreet(street);
        address.setNumber("10");
        address.setNeighborhood("Centro");
        address.setCity("Sao Paulo");
        address.setState("SP");
        address.setMain(main);
        return address;
    }
}
