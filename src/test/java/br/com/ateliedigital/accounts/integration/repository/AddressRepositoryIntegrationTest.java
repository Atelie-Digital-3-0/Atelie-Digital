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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static br.com.ateliedigital.accounts.integration.repository.AccountRepositoryIntegrationTest.account;
import static br.com.ateliedigital.accounts.integration.repository.AccountRepositoryIntegrationTest.address;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Consultas derivadas e constraints de {@link AddressRepository} validadas contra o Postgres real (migrations do Flyway).
 */
class AddressRepositoryIntegrationTest extends PostgresIntegrationTest {

    @Autowired AccountRepository accountRepository;
    @Autowired AddressRepository addressRepository;

    @Test
    @DisplayName("Addresses found by account id only from that account")
    void findAllByAccountId() {
        Account account = newAccount();
        Account other = newAccount();
        saveAddress(account, "Rua A", true);
        saveAddress(account, "Rua B", false);
        saveAddress(other, "Rua C", true);

        List<Address> addresses = addressRepository.findAllByAccountId(account.getId());

        assertEquals(2, addresses.size());
        assertTrue(addresses.stream().allMatch(a -> a.getAccount().getId().equals(account.getId())));
    }

    @Test
    @DisplayName("Addresses found by account id using pagination")
    void findAllByAccountIdPaginated() {
        Account account = newAccount();
        saveAddress(account, "Rua A", true);
        saveAddress(account, "Rua B", false);
        saveAddress(account, "Rua C", false);

        Page<Address> page = addressRepository.findAllByAccountId(account.getId(), PageRequest.of(0, 2));

        assertEquals(2, page.getContent().size());
        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    @DisplayName("Address found by id and account id")
    void findByIdAndAccountId() {
        Account account = newAccount();
        Address saved = saveAddress(account, "Rua A", true);

        assertEquals(saved.getId(), addressRepository.findByIdAndAccountId(saved.getId(), account.getId())
                .orElseThrow().getId());
    }

    @Test
    @DisplayName("Address not found because it belongs to another account")
    void findByIdAndAccountIdFromAnotherAccount() {
        Account account = newAccount();
        Account other = newAccount();
        Address saved = saveAddress(account, "Rua A", true);

        assertTrue(addressRepository.findByIdAndAccountId(saved.getId(), other.getId()).isEmpty());
        assertTrue(addressRepository.findByIdAndAccountId(UUID.randomUUID(), account.getId()).isEmpty());
    }

    @Test
    @DisplayName("Oldest address found by account id")
    void findFirstByAccountIdOrderByCreatedAtAscIdAsc() {
        Account account = newAccount();
        Address first = saveAddress(account, "Rua A", true);
        saveAddress(account, "Rua B", false);
        saveAddress(account, "Rua C", false);

        Address oldest = addressRepository.findFirstByAccountIdOrderByCreatedAtAscIdAsc(account.getId()).orElseThrow();

        assertEquals(first.getId(), oldest.getId());
        assertNotNull(oldest.getCreatedAt());
    }

    @Test
    @DisplayName("Oldest address not found because account has no address")
    void findFirstByAccountIdWhenThereIsNoAddress() {
        Account account = newAccount();

        assertTrue(addressRepository.findFirstByAccountIdOrderByCreatedAtAscIdAsc(account.getId()).isEmpty());
    }

    @Test
    @DisplayName("Address not saved because account already has a main address")
    void saveFailsWhenAccountAlreadyHasAMainAddress() {
        Account account = newAccount();
        saveAddress(account, "Rua A", true);

        assertThrows(DataIntegrityViolationException.class, () -> saveAddress(account, "Rua B", true));
    }

    @Test
    @DisplayName("Address saved with success as main in different accounts")
    void saveMainAddressInDifferentAccounts() {
        Account account = newAccount();
        Account other = newAccount();

        assertDoesNotThrow(() -> {
            saveAddress(account, "Rua A", true);
            saveAddress(other, "Rua A", true);
        });
    }

    private Account newAccount() {
        return accountRepository.saveAndFlush(account(TestUser.random()));
    }

    private Address saveAddress(Account account, String street, boolean main) {
        Address address = address(street, main);
        address.setAccount(account);
        return addressRepository.saveAndFlush(address);
    }
}
