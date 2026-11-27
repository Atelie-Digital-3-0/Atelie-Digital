package br.com.ateliedigital.product.mapper;

import br.com.ateliedigital.product.dto.responses.ProductResponses;
import br.com.ateliedigital.product.model.ProductImageModel;
import br.com.ateliedigital.storage.service.StorageService;
import org.springframework.stereotype.Component;

@Component
public class ProductImageMapper {

    private final StorageService storageService;

    public ProductImageMapper(StorageService storageService) {
        this.storageService = storageService;
    }

    public ProductResponses.ProductImageResponse toResponse(
            ProductImageModel image
    ) {
        if (image == null) {
            return null;
        }

        return new ProductResponses.ProductImageResponse(
                image.getId(),
                storageService.getPublicUrl(image.getStorageKey()),
                image.getDisplayOrder(),
                image.getCreatedAt()
        );
    }
}
