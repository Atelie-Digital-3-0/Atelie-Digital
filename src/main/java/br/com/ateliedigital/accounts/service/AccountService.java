package br.com.ateliedigital.accounts.service;

import br.com.ateliedigital.accounts.dto.AccountRequests.CompleteAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.event.AccountCreatedEvent;
import br.com.ateliedigital.accounts.event.SensitiveAccountUpdatedEvent;
import br.com.ateliedigital.accounts.integration.keycloak.KeycloakAdminClient;
import br.com.ateliedigital.accounts.mapper.AccountMapper;
import br.com.ateliedigital.accounts.model.Account;
import br.com.ateliedigital.accounts.repository.AccountRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final ApplicationEventPublisher events;
    private final AccountMapper mapper;

    public AccountService(
            AccountRepository accountRepository,
            KeycloakAdminClient keycloakAdminClient,
            ApplicationEventPublisher events,
            AccountMapper mapper) {
        this.accountRepository = accountRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (accountRepository.existsByCpf(request.cpf()) || accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF or email is already registered");
        }
        UUID keycloakId = keycloakAdminClient.createUser(request.email(), request.firstName(), request.lastName());
        Account account = mapper.toEntity(request);
        account.setKeycloakId(keycloakId);

        Account saved = accountRepository.save(account);
        keycloakAdminClient.sendAccountSetupEmail(keycloakId);

        events.publishEvent(new AccountCreatedEvent(saved.getId(),
                saved.getFirstName() + " " + saved.getLastName(), saved.getEmail(), Instant.now()));

        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Account getCurrent(JwtAuthenticationToken authentication) {
        return accountRepository.findByKeycloakId(keycloakId(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional
    public AccountResponse updateCurrent(
            JwtAuthenticationToken authentication,
            UpdateAccountRequest request
    ) {
        Account account = getCurrent(authentication);
        if (!account.getEmail().equalsIgnoreCase(request.email()) && accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }

        String oldEmail = account.getEmail();

        Account newAccount = mapper.toEntityUpdated(request);

        Account saved = accountRepository.save(newAccount);

        if (!oldEmail.equals(saved.getEmail())) {
            events.publishEvent(new SensitiveAccountUpdatedEvent(saved.getId(), "email", oldEmail, saved.getEmail(), Instant.now()));
        }

        return mapper.toResponse(saved);
    }

    public AccountResponse completeCurrent(
            JwtAuthenticationToken authentication,
            CompleteAccountRequest request
    ){
        Account account = getCurrent(authentication);
        if (!account.getCpf().equals(request.cpf()) && accountRepository.existsByCpf(request.cpf())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF is already registered");
        }

        String oldCpf = account.getCpf();

        Account newAccount = mapper.toEntityCompleted(request);

        Account saved = accountRepository.save(newAccount);

        if (!oldCpf.equals(saved.getCpf())) {
            events.publishEvent(new SensitiveAccountUpdatedEvent(saved.getId(), "cpf", oldCpf, saved.getCpf(), Instant.now()));
        }

        return mapper.toResponse(saved);
    }

    public void grantArtisanRole(UUID keycloakId) {
        if (accountRepository.findByKeycloakId(keycloakId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        keycloakAdminClient.grantArtisanRole(keycloakId);
    }

//    public String mercadoPagoToken(JwtAuthenticationToken authentication) {
//        keycloakId(authentication);
//        return keycloakAdminClient.mercadoPagoAccessToken(authentication.getToken().getTokenValue());
//    }

    public static UUID keycloakId(JwtAuthenticationToken authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is not a Keycloak UUID");
        }
    }
}
