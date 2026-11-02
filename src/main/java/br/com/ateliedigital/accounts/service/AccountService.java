package br.com.ateliedigital.accounts.service;

import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.event.AccountCreatedEvent;
import br.com.ateliedigital.accounts.event.SensitiveAccountUpdatedEvent;
import br.com.ateliedigital.accounts.integration.keycloak.KeycloakAdminClient;
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

    public AccountService(AccountRepository accountRepository, KeycloakAdminClient keycloakAdminClient,
                          ApplicationEventPublisher events) {
        this.accountRepository = accountRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.events = events;
    }

    @Transactional
    public Account create(CreateAccountRequest request) {
        if (accountRepository.existsByCpf(request.cpf()) || accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF or email is already registered");
        }
        UUID keycloakId = keycloakAdminClient.createUser(request.email(), request.firstName(), request.lastName());
        Account account = new Account();
        account.setKeycloakId(keycloakId);
        account.setFirstName(request.firstName());
        account.setLastName(request.lastName());
        account.setCellphone(request.cellphone());
        account.setDateOfBirth(request.dateOfBirth());
        account.setCpf(request.cpf());
        account.setEmail(request.email());
        Account saved = accountRepository.save(account);
        keycloakAdminClient.sendAccountSetupEmail(keycloakId);
        events.publishEvent(new AccountCreatedEvent(saved.getId(),
                saved.getFirstName() + " " + saved.getLastName(), saved.getEmail(), Instant.now()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Account getCurrent(JwtAuthenticationToken authentication) {
        return accountRepository.findByKeycloakId(keycloakId(authentication))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional
    public Account updateCurrent(JwtAuthenticationToken authentication, UpdateAccountRequest request) {
        Account account = getCurrent(authentication);
        if (!account.getCpf().equals(request.cpf()) && accountRepository.existsByCpf(request.cpf())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CPF is already registered");
        }
        if (!account.getEmail().equalsIgnoreCase(request.email()) && accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        String oldCpf = account.getCpf();
        String oldEmail = account.getEmail();
        account.setFirstName(request.firstName());
        account.setLastName(request.lastName());
        account.setCellphone(request.cellphone());
        account.setDateOfBirth(request.dateOfBirth());
        account.setCpf(request.cpf());
        account.setEmail(request.email());
        Account saved = accountRepository.save(account);
        if (!oldCpf.equals(saved.getCpf())) {
            events.publishEvent(new SensitiveAccountUpdatedEvent(saved.getId(), "cpf", oldCpf, saved.getCpf(), Instant.now()));
        }
        if (!oldEmail.equals(saved.getEmail())) {
            events.publishEvent(new SensitiveAccountUpdatedEvent(saved.getId(), "email", oldEmail, saved.getEmail(), Instant.now()));
        }
        return saved;
    }

    public void grantArtisanRole(UUID keycloakId) {
        if (!accountRepository.findByKeycloakId(keycloakId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        keycloakAdminClient.grantArtisanRole(keycloakId);
    }

    public String mercadoPagoToken(JwtAuthenticationToken authentication) {
        keycloakId(authentication);
        return keycloakAdminClient.mercadoPagoAccessToken(authentication.getToken().getTokenValue());
    }

    public static UUID keycloakId(JwtAuthenticationToken authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is not a Keycloak UUID");
        }
    }
}
