package br.com.ateliedigital.store.repository;

import br.com.ateliedigital.store.model.StoreModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


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

    boolean existsByArtisanIdAndDeletedAtIsNull(String artisanId);

    @EntityGraph(attributePaths = {"category", "address"})
    @Query("""
            select s from StoreModel s
            where s.deletedAt is null
              and lower(s.name) like :namePattern
              and (:categoryId is null or s.category.id = :categoryId)
            """)
    Page<StoreModel> search(
            @Param("namePattern") String namePattern,
            @Param("categoryId") UUID categoryId,
            Pageable pageable
    );

}
