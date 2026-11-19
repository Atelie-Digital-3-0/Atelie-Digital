package br.com.ateliedigital.accounts.service;

import br.com.ateliedigital.accounts.dto.AccountRequests.*;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.event.AccountCreatedEvent;
import br.com.ateliedigital.accounts.event.SensitiveAccountUpdatedEvent;
import br.com.ateliedigital.accounts.integration.keycloak.KeycloakAdminClient;
import br.com.ateliedigital.accounts.mapper.*;
import br.com.ateliedigital.accounts.model.*;
import br.com.ateliedigital.accounts.repository.AccountRepository;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private KeycloakAdminClient keycloakAdminClient;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private AccountMapper mapper;
    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private AccountService service;

    private UUID keycloakId;
    private JwtAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        keycloakId = UUID.randomUUID();
        authentication = authenticationFor(keycloakId.toString(), Map.of("email", "token@example.com"));
    }

    @AfterEach
    void tearDown() {
        // Os mocks sao recriados a cada teste pelo MockitoExtension; nada a limpar manualmente.
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("Account created with success")
    void createAccountSuccess() {
        CreateAccountRequest request = createRequest();
        Account entity = new Account();
        Account saved = savedAccount(UUID.randomUUID());
        AccountResponse response = responseFor(saved);

        when(accountRepository.existsByCpf(request.cpf())).thenReturn(false);
        when(accountRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(keycloakAdminClient.createUser(request.email(), request.firstName(), request.lastName()))
                .thenReturn(keycloakId);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(accountRepository.saveAndFlush(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        AccountResponse result = service.create(request);

        assertSame(response, result);
        assertEquals(keycloakId, entity.getKeycloakId());
        verify(keycloakAdminClient).sendAccountSetupEmail(keycloakId);

        AccountCreatedEvent event = captureEvent(AccountCreatedEvent.class);
        assertEquals(saved.getId(), event.id());
        assertEquals("Maria Silva", event.name());
        assertEquals("maria@example.com", event.email());
        assertNotNull(event.timestamp());
    }

    @Test
    @DisplayName("Account not created because CPF is registered")
    void createFailsWhenCpfIsAlreadyRegistered() {
        CreateAccountRequest request = createRequest();
        when(accountRepository.existsByCpf(request.cpf())).thenReturn(true);

        RuleConflit exception = assertThrows(RuleConflit.class, () -> service.create(request));

        assertEquals("CPF or email is already registered", exception.getMessage());
        verifyNoInteractions(keycloakAdminClient, events);
    }

    @Test
    @DisplayName("Account not created because Email is registered")
    void createFailsWhenEmailIsAlreadyRegistered() {
        CreateAccountRequest request = createRequest();
        when(accountRepository.existsByCpf(request.cpf())).thenReturn(false);
        when(accountRepository.existsByEmailIgnoreCase(request.email())).thenReturn(true);

        assertThrows(RuleConflit.class, () -> service.create(request));

        verifyNoInteractions(keycloakAdminClient, events);
    }

    @Test
    @DisplayName("Account not created by Keycloak failure")
    void createRemovesKeycloakUserWhenSavingFails() {
        CreateAccountRequest request = createRequest();
        Account entity = new Account();
        RuntimeException failure = new IllegalStateException("database down");

        when(keycloakAdminClient.createUser(request.email(), request.firstName(), request.lastName()))
                .thenReturn(keycloakId);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(accountRepository.saveAndFlush(entity)).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.create(request));

        assertSame(failure, thrown);
        verify(accountRepository).existsByCpf(request.cpf());
        verify(accountRepository).existsByEmailIgnoreCase(request.email());
        verify(keycloakAdminClient).deleteUser(keycloakId);
        verify(keycloakAdminClient, never()).sendAccountSetupEmail(any());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Account not created when send email with keycloak failure")
    void createRemovesKeycloakUserWhenSetupEmailFails() {
        CreateAccountRequest request = createRequest();
        Account entity = new Account();
        Account saved = savedAccount(UUID.randomUUID());
        RuntimeException failure = new IllegalStateException("smtp down");

        when(keycloakAdminClient.createUser(request.email(), request.firstName(), request.lastName()))
                .thenReturn(keycloakId);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(accountRepository.saveAndFlush(entity)).thenReturn(saved);
        doThrow(failure).when(keycloakAdminClient).sendAccountSetupEmail(keycloakId);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.create(request));

        assertSame(failure, thrown);
        verify(accountRepository).existsByCpf(request.cpf());
        verify(accountRepository).existsByEmailIgnoreCase(request.email());
        verify(keycloakAdminClient).deleteUser(keycloakId);
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Account not created when keycloak cleanup failure")
    void createKeepsOriginalExceptionWhenKeycloakCleanupAlsoFails() {
        CreateAccountRequest request = createRequest();
        Account entity = new Account();
        RuntimeException failure = new IllegalStateException("database down");
        RuntimeException cleanupFailure = new IllegalStateException("keycloak down");

        when(keycloakAdminClient.createUser(request.email(), request.firstName(), request.lastName()))
                .thenReturn(keycloakId);
        when(mapper.toEntity(request)).thenReturn(entity);
        when(accountRepository.saveAndFlush(entity)).thenThrow(failure);
        doThrow(cleanupFailure).when(keycloakAdminClient).deleteUser(keycloakId);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> service.create(request));

        assertSame(failure, thrown);
        assertEquals(List.of(cleanupFailure), List.of(thrown.getSuppressed()));
        verify(accountRepository).existsByCpf(request.cpf());
        verify(accountRepository).existsByEmailIgnoreCase(request.email());
        verifyNoInteractions(events);
    }

    // ------------------------------------------------------------ getCurrent

    @Test
    @DisplayName("Current account found with success")
    void getCurrent() {
        Account account = savedAccount(UUID.randomUUID());
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));

        assertSame(account, service.getCurrent(authentication));
    }

    @Test
    @DisplayName("Current account not found because account does not exist")
    void getCurrentFailsWhenAccountDoesNotExist() {
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class, () -> service.getCurrent(authentication));

        assertEquals("Account not found", exception.getMessage());
    }

    @Test
    @DisplayName("Current account not found because JWT subject is not a UUID")
    void getCurrentFailsWhenSubjectIsNotAUuid() {
        JwtAuthenticationToken invalid = authenticationFor("not-a-uuid", Map.of());

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.getCurrent(invalid));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(accountRepository);
    }

    // ----------------------------------------------------- getCurrentResponse

    @Test
    @DisplayName("Current account response returned with success")
    void getCurrentResponse() {
        Account account = savedAccount(UUID.randomUUID());
        AccountResponse response = responseFor(account);
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(mapper.toResponse(account)).thenReturn(response);

        assertSame(response, service.getCurrentResponse(authentication));
    }

    @Test
    @DisplayName("Current account response not returned because account does not exist")
    void getCurrentResponseFailsWhenAccountDoesNotExist() {
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getCurrentResponse(authentication));
    }

    // --------------------------------------------------------- updateCurrent

    @Test
    @DisplayName("Account updated with success changing Email and syncing Keycloak")
    void updateCurrentChangingEmailSyncsKeycloakAndPublishesEvent() {
        Account account = savedAccount(UUID.randomUUID());
        account.setKeycloakId(keycloakId);
        UpdateAccountRequest request = new UpdateAccountRequest(null, null, null, null, "new@example.com", null);
        AccountResponse response = responseFor(account);

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(accountRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        doAnswer(invocation -> {
            account.setEmail(request.email());
            return null;
        }).when(mapper).updateEntity(request, account);
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(mapper.toResponse(account)).thenReturn(response);

        AccountResponse result = service.updateCurrent(authentication, request);

        assertSame(response, result);
        verify(keycloakAdminClient).updateEmail(keycloakId, "new@example.com");

        SensitiveAccountUpdatedEvent event = captureEvent(SensitiveAccountUpdatedEvent.class);
        assertEquals(account.getId(), event.id());
        assertEquals("email", event.field());
        assertEquals("maria@example.com", event.oldValue());
        assertEquals("new@example.com", event.newValue());
        assertNotNull(event.timestamp());
    }

    @Test
    @DisplayName("Account not updated because Email is registered")
    void updateCurrentFailsWhenNewEmailIsAlreadyRegistered() {
        Account account = savedAccount(UUID.randomUUID());
        UpdateAccountRequest request = new UpdateAccountRequest(null, null, null, null, "taken@example.com", null);

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(accountRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        RuleConflit exception = assertThrows(RuleConflit.class, () -> service.updateCurrent(authentication, request));

        assertEquals("Email is already registered", exception.getMessage());
        verifyNoInteractions(keycloakAdminClient, events);
    }

    @Test
    @DisplayName("Account updated with success without changing Email")
    void updateCurrentWithoutEmailDoesNotTouchKeycloakNorPublishEvent() {
        Account account = savedAccount(UUID.randomUUID());
        UpdateAccountRequest request = new UpdateAccountRequest("Ana", null, null, null, null, "new bio");
        AccountResponse response = responseFor(account);

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(mapper.toResponse(account)).thenReturn(response);

        AccountResponse result = service.updateCurrent(authentication, request);

        assertSame(response, result);
        verify(mapper).updateEntity(request, account);
        verifyNoInteractions(keycloakAdminClient, events);
    }

    @Test
    @DisplayName("Account updated with success when Email differs only by case")
    void updateCurrentWithSameEmailIgnoringCaseIsNotAnEmailChange() {
        Account account = savedAccount(UUID.randomUUID());
        UpdateAccountRequest request = new UpdateAccountRequest(null, null, null, null, "MARIA@example.com", null);
        AccountResponse response = responseFor(account);

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        when(mapper.toResponse(account)).thenReturn(response);

        service.updateCurrent(authentication, request);

        verify(accountRepository, never()).existsByEmailIgnoreCase(any());
        verify(mapper).updateEntity(request, account);
        verifyNoInteractions(keycloakAdminClient, events);
    }

    @Test
    @DisplayName("Account not updated by Keycloak failure")
    void updateCurrentPropagatesKeycloakFailureAndDoesNotPublishEvent() {
        Account account = savedAccount(UUID.randomUUID());
        account.setKeycloakId(keycloakId);
        UpdateAccountRequest request = new UpdateAccountRequest(null, null, null, null, "new@example.com", null);
        RuntimeException failure = new IllegalStateException("keycloak down");

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(accountRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        doAnswer(invocation -> {
            account.setEmail(request.email());
            return null;
        }).when(mapper).updateEntity(request, account);
        when(accountRepository.saveAndFlush(account)).thenReturn(account);
        doThrow(failure).when(keycloakAdminClient).updateEmail(keycloakId, "new@example.com");

        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> service.updateCurrent(authentication, request));

        assertSame(failure, thrown);
        verifyNoInteractions(events);
    }

    // -------------------------------------------------------- completeCurrent

    @Test
    @DisplayName("Account completed with success creating account from token")
    void completeCurrentCreatesAccountFromTokenAndMarksFirstAddressAsMain() {
        CompleteAccountRequest request = completeRequest("12345678901");
        Address address = address("Rua A", false);
        AccountResponse response = responseFor(new Account());

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.empty());
        when(accountRepository.existsByEmailIgnoreCase("token@example.com")).thenReturn(false);
        when(accountRepository.existsByCpf(request.cpf())).thenReturn(false);
        when(addressMapper.toEntity(request.address())).thenReturn(address);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(Account.class))).thenReturn(response);

        AccountResponse result = service.completeCurrent(authentication, request);

        assertSame(response, result);
        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        Account account = captor.getValue();
        assertEquals(keycloakId, account.getKeycloakId());
        assertEquals("token@example.com", account.getEmail());
        assertEquals(List.of(address), account.getAddresses());
        assertTrue(address.isMain());
        assertSame(account, address.getAccount());
        verify(mapper).completeEntity(request, account);

        AccountCreatedEvent event = captureEvent(AccountCreatedEvent.class);
        assertEquals("token@example.com", event.email());
    }

    @Test
    @DisplayName("Account not completed because token has no Email")
    void completeCurrentFailsWhenTokenHasNoEmailClaim() {
        JwtAuthenticationToken withoutEmail = authenticationFor(keycloakId.toString(), Map.of());
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.completeCurrent(withoutEmail, completeRequest("12345678901")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Account not completed because token Email is blank")
    void completeCurrentFailsWhenTokenEmailIsBlank() {
        JwtAuthenticationToken blankEmail = authenticationFor(keycloakId.toString(), Map.of("email", "   "));
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.completeCurrent(blankEmail, completeRequest("12345678901")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Account not completed because token Email is registered")
    void completeCurrentFailsWhenTokenEmailIsAlreadyRegistered() {
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.empty());
        when(accountRepository.existsByEmailIgnoreCase("token@example.com")).thenReturn(true);

        RuleConflit exception = assertThrows(RuleConflit.class,
                () -> service.completeCurrent(authentication, completeRequest("12345678901")));

        assertEquals("Email is already registered", exception.getMessage());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Account not completed because CPF is registered")
    void completeCurrentFailsWhenCpfIsAlreadyRegisteredForNewAccount() {
        CompleteAccountRequest request = completeRequest("12345678901");
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.empty());
        when(accountRepository.existsByEmailIgnoreCase("token@example.com")).thenReturn(false);
        when(accountRepository.existsByCpf(request.cpf())).thenReturn(true);

        RuleConflit exception = assertThrows(RuleConflit.class, () -> service.completeCurrent(authentication, request));

        assertEquals("CPF is already registered", exception.getMessage());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Account completed with success keeping the same CPF")
    void completeCurrentOnExistingAccountWithSameCpfPublishesNoEvent() {
        Account account = savedAccount(UUID.randomUUID());
        account.setCpf("12345678901");
        CompleteAccountRequest request = completeRequest("12345678901");
        Address address = address("Rua A", false);
        AccountResponse response = responseFor(account);

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(addressMapper.toEntity(request.address())).thenReturn(address);
        when(accountRepository.save(account)).thenReturn(account);
        when(mapper.toResponse(account)).thenReturn(response);

        AccountResponse result = service.completeCurrent(authentication, request);

        assertSame(response, result);
        verify(accountRepository, never()).existsByCpf(any());
        verify(accountRepository, never()).existsByEmailIgnoreCase(any());
        verify(mapper).completeEntity(request, account);
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Account completed with success changing CPF")
    void completeCurrentOnExistingAccountWithNewCpfPublishesSensitiveUpdate() {
        Account account = savedAccount(UUID.randomUUID());
        account.setCpf("11111111111");
        CompleteAccountRequest request = completeRequest("22222222222");
        Address address = address("Rua A", false);
        AccountResponse response = responseFor(account);

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(accountRepository.existsByCpf("22222222222")).thenReturn(false);
        doAnswer(invocation -> {
            account.setCpf(request.cpf());
            return null;
        }).when(mapper).completeEntity(request, account);
        when(addressMapper.toEntity(request.address())).thenReturn(address);
        when(accountRepository.save(account)).thenReturn(account);
        when(mapper.toResponse(account)).thenReturn(response);

        service.completeCurrent(authentication, request);

        SensitiveAccountUpdatedEvent event = captureEvent(SensitiveAccountUpdatedEvent.class);
        assertEquals(account.getId(), event.id());
        assertEquals("cpf", event.field());
        assertEquals("11111111111", event.oldValue());
        assertEquals("22222222222", event.newValue());
    }

    @Test
    @DisplayName("Account not completed because new CPF belongs to another account")
    void completeCurrentFailsWhenNewCpfBelongsToAnotherAccount() {
        Account account = savedAccount(UUID.randomUUID());
        account.setCpf("11111111111");
        CompleteAccountRequest request = completeRequest("22222222222");

        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(accountRepository.existsByCpf("22222222222")).thenReturn(true);

        assertThrows(RuleConflit.class, () -> service.completeCurrent(authentication, request));

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    @DisplayName("Account completed with success reusing equivalent address")
    void completeCurrentReusesEquivalentAddressInsteadOfDuplicatingIt() {
        Address existing = address("Rua A", true);
        Account account = accountWithAddresses(existing);
        CompleteAccountRequest request = completeRequest(account.getCpf());
        Address incoming = address("  rua a ", false);

        stubExistingAccountCompletion(account, request, incoming);

        service.completeCurrent(authentication, request);

        assertEquals(List.of(existing), account.getAddresses());
        assertTrue(existing.isMain());
        verify(accountRepository, never()).flush();
    }

    @Test
    @DisplayName("Account completed with success promoting equivalent address as main")
    void completeCurrentPromotesEquivalentAddressWhenRequestMarksItAsMain() {
        Address existing = address("Rua A", false);
        Address otherMain = address("Rua B", true);
        Account account = accountWithAddresses(existing, otherMain);
        CompleteAccountRequest request = completeRequest(account.getCpf());
        Address incoming = address("Rua A", true);

        stubExistingAccountCompletion(account, request, incoming);

        service.completeCurrent(authentication, request);

        assertEquals(2, account.getAddresses().size());
        assertTrue(existing.isMain());
        assertFalse(otherMain.isMain());
        verify(accountRepository).flush();
    }

    @Test
    @DisplayName("Account completed with success promoting equivalent address as main when there is no other main")
    void completeCurrentPromotesEquivalentAddressWithoutFlushWhenNoOtherMainExists() {
        Address existing = address("Rua A", false);
        Account account = accountWithAddresses(existing);
        CompleteAccountRequest request = completeRequest(account.getCpf());
        Address incoming = address("Rua A", true);

        stubExistingAccountCompletion(account, request, incoming);

        service.completeCurrent(authentication, request);

        assertTrue(existing.isMain());
        verify(accountRepository, never()).flush();
    }

    @Test
    @DisplayName("Account completed with success adding new main address")
    void completeCurrentAddsNewMainAddressAndDemotesCurrentMain() {
        Address currentMain = address("Rua A", true);
        Account account = accountWithAddresses(currentMain);
        CompleteAccountRequest request = completeRequest(account.getCpf());
        Address incoming = address("Rua B", true);

        stubExistingAccountCompletion(account, request, incoming);

        service.completeCurrent(authentication, request);

        assertEquals(List.of(currentMain, incoming), account.getAddresses());
        assertFalse(currentMain.isMain());
        assertTrue(incoming.isMain());
        assertSame(account, incoming.getAccount());
        verify(accountRepository).flush();
    }

    @Test
    @DisplayName("Account completed with success adding new non-main address")
    void completeCurrentAddsNonMainAddressKeepingCurrentMain() {
        Address currentMain = address("Rua A", true);
        Account account = accountWithAddresses(currentMain);
        CompleteAccountRequest request = completeRequest(account.getCpf());
        Address incoming = address("Rua B", false);

        stubExistingAccountCompletion(account, request, incoming);

        service.completeCurrent(authentication, request);

        assertEquals(List.of(currentMain, incoming), account.getAddresses());
        assertTrue(currentMain.isMain());
        assertFalse(incoming.isMain());
        verify(accountRepository, never()).flush();
    }

    // ------------------------------------------------------ grantArtisanRole

    @Test
    @DisplayName("Artisan role granted with success")
    void grantArtisanRole() {
        when(accountRepository.findByKeycloakId(keycloakId))
                .thenReturn(java.util.Optional.of(savedAccount(UUID.randomUUID())));

        service.grantArtisanRole(keycloakId);

        verify(keycloakAdminClient).grantArtisanRole(keycloakId);
    }

    @Test
    @DisplayName("Artisan role not granted because account does not exist")
    void grantArtisanRoleFailsWhenAccountDoesNotExist() {
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class, () -> service.grantArtisanRole(keycloakId));

        assertEquals("Account not found", exception.getMessage());
        verifyNoInteractions(keycloakAdminClient);
    }

    // ------------------------------------------------------------ keycloakId

    @Test
    @DisplayName("Keycloak id extracted from JWT with success")
    void keycloakId() {
        assertEquals(keycloakId, AccountService.keycloakId(authentication));
    }

    @Test
    @DisplayName("Keycloak id not extracted because JWT subject is not a UUID")
    void keycloakIdFailsWhenSubjectIsNotAUuid() {
        JwtAuthenticationToken invalid = authenticationFor("not-a-uuid", Map.of());

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> AccountService.keycloakId(invalid));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("JWT subject is not a Keycloak UUID", exception.getReason());
    }

    // --------------------------------------------------------------- helpers

    private void stubExistingAccountCompletion(Account account, CompleteAccountRequest request, Address incoming) {
        when(accountRepository.findByKeycloakId(keycloakId)).thenReturn(java.util.Optional.of(account));
        when(addressMapper.toEntity(request.address())).thenReturn(incoming);
        when(accountRepository.save(account)).thenReturn(account);
        when(mapper.toResponse(account)).thenReturn(responseFor(account));
    }

    private <T> T captureEvent(Class<T> type) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(captor.capture());
        assertTrue(type.isInstance(captor.getValue()), "unexpected event " + captor.getValue());
        return type.cast(captor.getValue());
    }

    private static JwtAuthenticationToken authenticationFor(String subject, Map<String, Object> extraClaims) {
        Map<String, Object> claims = new HashMap<>(extraClaims);
        claims.put("sub", subject);
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
    }

    private static CreateAccountRequest createRequest() {
        return new CreateAccountRequest("Maria", "Silva", "11999999999",
                LocalDate.of(1990, 5, 20), "12345678901", "maria@example.com");
    }

    private static CompleteAccountRequest completeRequest(String cpf) {
        AddressRequest address = new AddressRequest("01001000", "Rua A", "10", null, "Centro", "Sao Paulo", "SP", false);
        return new CompleteAccountRequest("Maria", "Silva", "11999999999",
                LocalDate.of(1990, 5, 20), cpf, "bio", address);
    }

    private static Account savedAccount(UUID id) {
        Account account = new Account();
        account.setId(id);
        account.setFirstName("Maria");
        account.setLastName("Silva");
        account.setCellphone("11999999999");
        account.setDateOfBirth(LocalDate.of(1990, 5, 20));
        account.setCpf("12345678901");
        account.setEmail("maria@example.com");
        return account;
    }

    private static Account accountWithAddresses(Address... addresses) {
        Account account = savedAccount(UUID.randomUUID());
        for (Address address : addresses) {
            account.addAddress(address);
        }
        return account;
    }

    private static Address address(String street, boolean main) {
        Address address = new Address();
        address.setZipCode("01001000");
        address.setStreet(street);
        address.setNumber("10");
        address.setNeighborhood("Centro");
        address.setCity("Sao Paulo");
        address.setState("SP");
        address.setMain(main);
        return address;
    }

    private static AccountResponse responseFor(Account account) {
        return new AccountResponse(account.getId(), account.getKeycloakId(), account.getFirstName(),
                account.getLastName(), account.getCellphone(), account.getDateOfBirth(), account.getCpf(),
                account.getEmail(), account.getBio(), List.of());
    }
}
