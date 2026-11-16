package br.com.ateliedigital.store.repository;

import br.com.ateliedigital.store.model.AddressModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<AddressRepository, UUID> {

    Optional<AddressModel> findByStore_Id(UUID storeId);
}
