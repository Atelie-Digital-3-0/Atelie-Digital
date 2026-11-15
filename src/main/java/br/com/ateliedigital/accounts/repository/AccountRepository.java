package br.com.ateliedigital.accounts.repository;

import br.com.ateliedigital.accounts.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByKeycloakId(UUID keycloakId);
    boolean existsByCpf(String cpf);
    boolean existsByEmailIgnoreCase(String email);
}
