package br.com.ateliedigital.product.repository;

import br.com.ateliedigital.product.model.ProductImageModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImageModel, UUID>{

    @Query("""
            SELECT i
            FROM ProductImageModel i
            JOIN i.variation v
            JOIN v.product p
            WHERE v.id = :variationId
              AND i.isDeleted = false
              AND v.isDeleted = false
              AND p.isDeleted = false
            ORDER BY i.displayOrder ASC
            """)
    List<ProductImageModel> findActiveByVariationId(
            @Param("variationId") UUID variationId
    );

    @Query("""
            SELECT i
            FROM ProductImageModel i
            JOIN i.variation v
            JOIN v.product p
            WHERE i.id = :imageId
              AND i.isDeleted = false
              AND v.isDeleted = false
              AND p.isDeleted = false
            """)
    Optional<ProductImageModel> findActiveById(
            @Param("imageId") UUID imageId
    );

    @Query("""
        SELECT i
        FROM ProductImageModel i
        JOIN i.variation v
        JOIN v.product p
        WHERE v.id = :variationId
          AND i.isDeleted = false
          AND v.isDeleted = false
          AND p.isDeleted = false
        ORDER BY i.displayOrder ASC
        """)
    List<ProductImageModel> findFirstActiveByVariationId(
            @Param("variationId") UUID variationId,
            Pageable pageable
    );
}
