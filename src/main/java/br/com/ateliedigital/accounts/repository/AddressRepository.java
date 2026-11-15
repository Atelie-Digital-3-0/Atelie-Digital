package br.com.ateliedigital.accounts.repository;

import br.com.ateliedigital.accounts.model.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    Page<Address> findAllByAccountId(UUID accountId);
    Optional<Address> findByIdAndAccountId(UUID id, UUID accountId);
}
