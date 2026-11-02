package br.com.ateliedigital.accounts.mapper;

import br.com.ateliedigital.accounts.dto.AccountRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.dto.AccountResponses.AddressResponse;
import br.com.ateliedigital.accounts.model.Account;
import br.com.ateliedigital.accounts.model.Address;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountResponse toResponse(Account account);
    AddressResponse toResponse(Address address);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "account", ignore = true)
    Address toAddress(AddressRequest request);
}
