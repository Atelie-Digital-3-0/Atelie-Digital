package br.com.ateliedigital.product.dto.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ProductResponses {
    public record ProductResponse(
            UUID id,
            UUID storeId,
            String name,
            String description,
            List<ProductVariationResponse> variations,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record ProductVariationResponse(
            UUID id,
            BigDecimal price,
            BigDecimal weight,
            BigDecimal length,
            BigDecimal width,
            BigDecimal height,
            String sku,
            Integer stock,
            String color,
            String size,
            boolean primary,
            List<ProductImageResponse> images,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record ProductImageResponse(
            UUID id,
            String url,
            Integer displayOrder,
            LocalDateTime createdAt
    ) {}
}
