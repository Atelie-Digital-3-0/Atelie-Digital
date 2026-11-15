package br.com.ateliedigital.accounts.mapper;

import br.com.ateliedigital.accounts.dto.AccountRequests.CompleteAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.model.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    Account toEntity(CreateAccountRequest data);

    Account toEntityUpdated(UpdateAccountRequest data);

    Account toEntityCompleted(CompleteAccountRequest data);

    AccountResponse toResponse(Account account);
}
