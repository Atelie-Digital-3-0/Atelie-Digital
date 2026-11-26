
package br.com.ateliedigital.product.mapper;

import br.com.ateliedigital.product.dto.responses.ProductResponses;
import br.com.ateliedigital.product.model.ProductImageModel;
import br.com.ateliedigital.storage.service.StorageService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ProductImageMapper {

    @Autowired
    protected StorageService storageService;

    @Mapping(
            target = "url",
            expression = "java(storageService.getPublicUrl(image.getStorageKey()))"
    )
    public abstract ProductResponses.ProductImageResponse toResponse(
            ProductImageModel image
    );
}
