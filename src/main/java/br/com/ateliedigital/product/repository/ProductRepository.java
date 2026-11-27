
package br.com.ateliedigital.product.repository;

import br.com.ateliedigital.product.model.ProductModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<ProductModel, UUID> {

    @Query("""
            SELECT p
            FROM ProductModel p
            WHERE p.id = :productId
              AND p.isDeleted = false
            """)
    Optional<ProductModel> findActiveById(
            @Param("productId") UUID productId
    );

    @Query("""
            SELECT p
            FROM ProductModel p
            WHERE p.storeId = :storeId
              AND p.isDeleted = false
            """)
    Page<ProductModel> findActiveByStoreId(
            @Param("storeId") UUID storeId,
            Pageable pageable
    );

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM ProductModel p
            WHERE p.id = :productId
              AND p.storeId = :storeId
              AND p.isDeleted = false
            """)
    boolean existsActiveByIdAndStoreId(
            @Param("productId") UUID productId,
            @Param("storeId") UUID storeId
    );
}
