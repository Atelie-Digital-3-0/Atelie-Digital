package br.com.ateliedigital.store.mapper;


import br.com.ateliedigital.store.dto.responses.StoreResponses;
import br.com.ateliedigital.store.model.CategoryModel;
import org.mapstruct.Mapper;



@Mapper(componentModel = "spring")
public interface CategoryMapper {
    StoreResponses.CategoryResponse toResponse(CategoryModel category);
}
