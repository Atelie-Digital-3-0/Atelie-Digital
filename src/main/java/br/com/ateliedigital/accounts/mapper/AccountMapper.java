package br.com.ateliedigital.accounts.mapper;

import br.com.ateliedigital.accounts.dto.AccountRequests.CompleteAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.model.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "bio", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    Account toEntity(CreateAccountRequest data);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "cpf", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    void updateEntity(UpdateAccountRequest data, @MappingTarget Account account);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "keycloakId", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "addresses", ignore = true)
    void completeEntity(CompleteAccountRequest data, @MappingTarget Account account);

    AccountResponse toResponse(Account account);
}
