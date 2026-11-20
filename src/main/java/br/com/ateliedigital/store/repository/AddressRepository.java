package br.com.ateliedigital.store.repository;

import br.com.ateliedigital.store.model.AddressModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository("storeAddressRepository")
public interface AddressRepository extends JpaRepository<AddressModel, UUID> {

    Optional<AddressModel> findByStore_Id(UUID storeId);
}
