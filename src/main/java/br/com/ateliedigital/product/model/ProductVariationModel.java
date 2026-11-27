package br.com.ateliedigital.product.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_variations", schema = "product")
@Getter
@Setter
@NoArgsConstructor
public class ProductVariationModel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductModel product;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 10, scale = 3)
    private BigDecimal weight;

    @Column(precision = 10, scale = 3)
    private BigDecimal length;

    @Column(precision = 10, scale = 3)
    private BigDecimal width;

    @Column(precision = 10, scale = 3)
    private BigDecimal height;

    @Column(length = 50)
    private String sku;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(length = 100)
    private String color;

    @Column(length = 100)
    private String size;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "variation", fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<ProductImageModel> images = new ArrayList<>();
}
