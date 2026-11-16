package br.com.ateliedigital.store.repository;

import br.com.ateliedigital.store.model.StoreModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<StoreModel, UUID> {

    @EntityGraph(attributePaths = {"category", "address"})
    Optional<StoreModel> findByIdAndDeletedAtIsNull(UUID id);

    @EntityGraph(attributePaths = {"category", "address"})
    Optional<StoreModel> findByArtisanIdAndDeletedAtIsNull(String artisanId);

    @EntityGraph(attributePaths = {"category", "address"})
    Page<StoreModel> findByDeletedAtIsNull(Pageable pageable);

    boolean existsByCategory_IdAndDeletedAtIsNull(UUID categoryId);



}
