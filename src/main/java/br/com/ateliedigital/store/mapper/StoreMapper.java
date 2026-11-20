package br.com.ateliedigital.store.mapper;

import br.com.ateliedigital.store.dto.requests.StoreRequests;
import br.com.ateliedigital.store.dto.responses.StoreResponses;
import br.com.ateliedigital.store.model.StoreModel;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {StoreAddressMapper.class, CategoryMapper.class})
public interface StoreMapper {

    // Criação: Request -> Entity
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "category.id", source = "categoryId")
    @Mapping(target = "address", source = "address")
    StoreModel toEntity(StoreRequests.CreateStoreRequest request);

    // Leitura: Entity -> Response
    StoreResponses.StorePublicResponse toResponse(StoreModel store);

    // Atualização (PATCH): Request -> Entity Existente
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "artisanId", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    void updateEntity(StoreRequests.UpdateStoreRequest request, @MappingTarget StoreModel store);


    //  Relacionamento Bidirecional na criação
    @AfterMapping
    default void linkAddressToStore(StoreRequests.CreateStoreRequest request, @MappingTarget StoreModel store) {
        if (store.getAddress() != null) {
            store.getAddress().setStore(store);
        }
    }

    @AfterMapping
    default void linkAddressToStoreOnUpdate(StoreRequests.UpdateStoreRequest request, @MappingTarget StoreModel store) {
        if (store.getAddress() != null) {
            store.getAddress().setStore(store);
        }
    }
}
