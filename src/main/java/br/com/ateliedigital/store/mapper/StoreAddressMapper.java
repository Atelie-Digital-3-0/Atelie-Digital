package br.com.ateliedigital.store.mapper;

import br.com.ateliedigital.store.dto.requests.StoreAddressRequests;
import br.com.ateliedigital.store.dto.responses.StoreAddressResponses;
import br.com.ateliedigital.store.model.AddressModel;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface StoreAddressMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "store", ignore = true) // Será preenchido pelo @AfterMapping do StoreMapper
    AddressModel toEntity(StoreAddressRequests.AddressRequest request);

    StoreAddressResponses.AddressResponse toResponse(AddressModel address);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "store", ignore = true)
    void updateEntity(StoreAddressRequests.AddressUpdateRequest request, @MappingTarget AddressModel address);
}
