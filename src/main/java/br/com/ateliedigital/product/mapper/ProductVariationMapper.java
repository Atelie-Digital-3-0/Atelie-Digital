package br.com.ateliedigital.product.mapper;

import br.com.ateliedigital.product.dto.requests.ProductRequests;
import br.com.ateliedigital.product.dto.responses.ProductResponses;
import br.com.ateliedigital.product.model.ProductVariationModel;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring"
)
public interface ProductVariationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "primary", source = "primary")
    ProductVariationModel toEntity(
            ProductRequests.ProductVariationRequest request
    );

    @Mapping(target = "images", ignore = true)
    @Mapping(target = "primary", source = "primary")
    ProductResponses.ProductVariationResponse toResponse(
            ProductVariationModel variation
    );

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "images", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @BeanMapping(
            nullValuePropertyMappingStrategy =
                    NullValuePropertyMappingStrategy.IGNORE
    )
    void updateEntity(
            ProductRequests.UpdateProductVariationRequest request,
            @MappingTarget ProductVariationModel variation
    );


}
