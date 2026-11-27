package br.com.ateliedigital.product.repository;

import br.com.ateliedigital.product.model.ProductVariationModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariationRepository extends JpaRepository<ProductVariationModel, UUID> {

    @Query("""
            SELECT v
            FROM ProductVariationModel v
            JOIN v.product p
            WHERE p.id = :productId
              AND p.isDeleted = false
              AND v.isDeleted = false
            ORDER BY v.isPrimary DESC, v.createdAt ASC
            """)
    List<ProductVariationModel> findActiveByProductId(
            @Param("productId") UUID productId
    );

    @Query("""
            SELECT v
            FROM ProductVariationModel v
            JOIN v.product p
            WHERE v.id = :variationId
              AND p.isDeleted = false
              AND v.isDeleted = false
            """)
    Optional<ProductVariationModel> findActiveById(
            @Param("variationId") UUID variationId
    );

    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM ProductVariationModel v
            WHERE v.sku = :sku
            """)
    boolean existsBySkuIncludingDeleted(
            @Param("sku") String sku
    );

    @Query("""
            SELECT CASE WHEN COUNT(v) > 0 THEN true ELSE false END
            FROM ProductVariationModel v
            WHERE v.sku = :sku
              AND v.id <> :variationId
            """)
    boolean existsBySkuAndIdNotIncludingDeleted(
            @Param("sku") String sku,
            @Param("variationId") UUID variationId
    );

}
