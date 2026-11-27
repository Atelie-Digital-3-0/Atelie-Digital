package br.com.ateliedigital.product.mapper;

import br.com.ateliedigital.product.dto.requests.ProductRequests;
import br.com.ateliedigital.product.dto.responses.ProductResponses;
import br.com.ateliedigital.product.model.ProductModel;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        uses = ProductVariationMapper.class
)
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "storeId", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductModel toEntity(
            ProductRequests.CreateProductRequest request
    );
    @Mapping(target = "storeId", source = "storeId")
    @Mapping(target = "variations", ignore = true)
    ProductResponses.ProductResponse toResponse(
            ProductModel product
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "storeId", ignore = true)
    @Mapping(target = "variations", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    void updateEntity(
            ProductRequests.UpdateProductRequest request,
            @MappingTarget ProductModel product
    );

    @AfterMapping
    default void linkVariations(
            @MappingTarget ProductModel product
    ) {
        if (product.getVariations() != null) {
            product.getVariations().forEach(
                    variation -> variation.setProduct(product)
            );
        }
    }

}
