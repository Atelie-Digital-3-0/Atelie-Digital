package br.com.ateliedigital.accounts.service;

import br.com.ateliedigital.accounts.dto.AccountRequests.CompleteAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.event.AccountCreatedEvent;
import br.com.ateliedigital.accounts.event.SensitiveAccountUpdatedEvent;
import br.com.ateliedigital.accounts.integration.keycloak.KeycloakAdminClient;
import br.com.ateliedigital.accounts.mapper.AccountMapper;
import br.com.ateliedigital.accounts.mapper.AddressMapper;
import br.com.ateliedigital.accounts.model.Account;
import br.com.ateliedigital.accounts.model.Address;
import br.com.ateliedigital.accounts.repository.AccountRepository;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
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
    private final AddressMapper addressMapper;

    public AccountService(
            AccountRepository accountRepository,
            KeycloakAdminClient keycloakAdminClient,
            ApplicationEventPublisher events,
            AccountMapper mapper,
            AddressMapper addressMapper) {
        this.accountRepository = accountRepository;
        this.keycloakAdminClient = keycloakAdminClient;
        this.events = events;
        this.mapper = mapper;
        this.addressMapper = addressMapper;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (accountRepository.existsByCpf(request.cpf()) || accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new RuleConflit("CPF or email is already registered");
        }
        UUID keycloakId = keycloakAdminClient.createUser(request.email(), request.firstName(), request.lastName());
        Account saved;
        try {
            Account account = mapper.toEntity(request);
            account.setKeycloakId(keycloakId);

            saved = accountRepository.saveAndFlush(account);
            keycloakAdminClient.sendAccountSetupEmail(keycloakId);
        } catch (RuntimeException exception) {
            // Sem a conta local ou sem o e-mail de cadastro o usuario nao consegue acessar:
            // remove o usuario do Keycloak para permitir um novo cadastro com o mesmo e-mail.
            deleteKeycloakUserQuietly(keycloakId, exception);
            throw exception;
        }

        publishCreated(saved);

        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Account getCurrent(JwtAuthenticationToken authentication) {
        return accountRepository.findByKeycloakId(keycloakId(authentication))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    @Transactional(readOnly = true)
    public AccountResponse getCurrentResponse(JwtAuthenticationToken authentication) {
        return mapper.toResponse(getCurrent(authentication));
    }

    @Transactional
    public AccountResponse updateCurrent(
            JwtAuthenticationToken authentication,
            UpdateAccountRequest request
    ) {
        Account account = getCurrent(authentication);
        boolean emailChanged = !account.getEmail().equalsIgnoreCase(request.email());
        if (emailChanged && accountRepository.existsByEmailIgnoreCase(request.email())) {
            throw new RuleConflit("Email is already registered");
        }

        String oldEmail = account.getEmail();

        mapper.updateEntity(request, account);

        Account saved = accountRepository.saveAndFlush(account);

        if (emailChanged) {
            // O e-mail e o login no realm: mantem o Keycloak sincronizado (falha aqui desfaz a transacao).
            keycloakAdminClient.updateEmail(saved.getKeycloakId(), saved.getEmail());
            events.publishEvent(new SensitiveAccountUpdatedEvent(saved.getId(), "email", oldEmail, saved.getEmail(), Instant.now()));
        }

        return mapper.toResponse(saved);
    }

    /**
     * Completa o cadastro do usuario autenticado. Usuarios que se cadastraram pela tela do Keycloak
     * ou pelo login com Google ainda nao possuem conta local: ela e criada a partir do token.
     */
    @Transactional
    public AccountResponse completeCurrent(
            JwtAuthenticationToken authentication,
            CompleteAccountRequest request
    ){
        UUID keycloakId = keycloakId(authentication);
        Account account = accountRepository.findByKeycloakId(keycloakId).orElse(null);
        boolean newAccount = account == null;
        if (newAccount) {
            String email = authentication.getToken().getClaimAsString("email");
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT has no email claim");
            }
            if (accountRepository.existsByEmailIgnoreCase(email)) {
                throw new RuleConflit("Email is already registered");
            }
            account = new Account();
            account.setKeycloakId(keycloakId);
            account.setEmail(email);
        }

        String oldCpf = account.getCpf();
        if (!request.cpf().equals(oldCpf) && accountRepository.existsByCpf(request.cpf())) {
            throw new RuleConflit("CPF is already registered");
        }

        mapper.completeEntity(request, account);
        Address address = addressMapper.toEntity(request.address());
        if (account.getAddresses().isEmpty()) {
            address.setMain(true);
        } else if (address.isMain()) {
            account.getAddresses().forEach(existing -> existing.setMain(false));
        }
        account.addAddress(address);

        Account saved = accountRepository.save(account);

        if (newAccount) {
            publishCreated(saved);
        } else if (!request.cpf().equals(oldCpf)) {
            events.publishEvent(new SensitiveAccountUpdatedEvent(saved.getId(), "cpf", oldCpf, saved.getCpf(), Instant.now()));
        }

        return mapper.toResponse(saved);
    }

    public void grantArtisanRole(UUID keycloakId) {
        if (accountRepository.findByKeycloakId(keycloakId).isEmpty()) {
            throw new ResourceNotFoundException("Account not found");
        }
        keycloakAdminClient.grantArtisanRole(keycloakId);
    }


    private void publishCreated(Account account) {
        events.publishEvent(new AccountCreatedEvent(account.getId(),
                account.getFirstName() + " " + account.getLastName(), account.getEmail(), Instant.now()));
    }

    private void deleteKeycloakUserQuietly(UUID keycloakId, RuntimeException cause) {
        try {
            keycloakAdminClient.deleteUser(keycloakId);
        } catch (RuntimeException deleteException) {
            cause.addSuppressed(deleteException);
        }
    }

    public static UUID keycloakId(JwtAuthenticationToken authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is not a Keycloak UUID");
        }
    }
}
