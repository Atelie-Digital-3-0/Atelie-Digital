package br.com.ateliedigital.accounts.security;

import br.com.ateliedigital.accounts.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountCompletionInterceptorTest {
    private static final AccountCompletionProperties PROPERTIES =
            new AccountCompletionProperties(Duration.ofMinutes(5), 100);

    private final UUID keycloakId = UUID.randomUUID();
    private AccountRepository repository;
    private AccountCompletionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        repository = mock(AccountRepository.class);
        interceptor = new AccountCompletionInterceptor(repository, PROPERTIES);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksProtectedRouteWhenAccountDoesNotExist() {
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> preHandle("GET", "/api/accounts/me/addresses"));

        assertEquals(HttpStatus.FORBIDDEN.value(), exception.getStatusCode().value());
        assertEquals(AccountCompletionInterceptor.ERROR_CODE, exception.getReason());
    }

    @Test
    void allowsProtectedRouteWhenAccountExists() {
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(true);

        assertDoesNotThrow(() -> preHandle("PATCH", "/api/accounts/me"));
    }

    @Test
    void allowsRoutesNeededToCompleteRegistration() {
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(false);

        assertDoesNotThrow(() -> preHandle("GET", "/api/accounts/me"));
        assertDoesNotThrow(() -> preHandle("PUT", "/api/accounts/me/complete"));
        assertDoesNotThrow(() -> preHandle("GET", "/api/accounts/me/addresses/cep/01001000"));
    }

    @Test
    void stillBlocksOtherMethodsOnExemptPaths() {
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(false);

        assertThrows(ResponseStatusException.class, () -> preHandle("PATCH", "/api/accounts/me"));
        assertThrows(ResponseStatusException.class, () -> preHandle("POST", "/api/stores"));
    }

    @Test
    void allowsPublicReadRoutes() {
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(false);

        assertDoesNotThrow(() -> preHandle("GET", "/api/products/123"));
        assertDoesNotThrow(() -> preHandle("GET", "/api/stores"));
    }

    @Test
    void skipsAdminAndAnonymousRequests() {
        authenticate("ROLE_ADMIN");
        assertDoesNotThrow(() -> preHandle("GET", "/api/admin/anything"));

        SecurityContextHolder.clearContext();
        assertDoesNotThrow(() -> preHandle("GET", "/api/accounts/me/addresses"));

        verifyNoInteractions(repository);
    }

    @Test
    void cachesExistingAccountAndSkipsRepositoryOnNextRequests() {
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(true);

        preHandle("GET", "/api/accounts/me/addresses");
        preHandle("GET", "/api/accounts/me/addresses");
        preHandle("PATCH", "/api/accounts/me");

        verify(repository, times(1)).existsByKeycloakId(keycloakId);
    }

    @Test
    void doesNotCacheMissingAccountSoCompletionIsRecognizedImmediately() {
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(false, true);

        assertThrows(ResponseStatusException.class, () -> preHandle("GET", "/api/accounts/me/addresses"));
        assertDoesNotThrow(() -> preHandle("GET", "/api/accounts/me/addresses"));

        verify(repository, times(2)).existsByKeycloakId(keycloakId);
    }

    @Test
    void zeroMaxSizeDisablesCache() {
        interceptor = new AccountCompletionInterceptor(repository, new AccountCompletionProperties(Duration.ofMinutes(5), 0));
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(true);

        preHandle("GET", "/api/accounts/me/addresses");
        preHandle("GET", "/api/accounts/me/addresses");

        verify(repository, times(2)).existsByKeycloakId(keycloakId);
    }

    @Test
    void queriesRepositoryAgainAfterCacheExpires() {
        AtomicLong nanos = new AtomicLong();
        interceptor = new AccountCompletionInterceptor(repository, PROPERTIES, nanos::get);
        authenticate("ROLE_CLIENT");
        when(repository.existsByKeycloakId(keycloakId)).thenReturn(true);

        preHandle("GET", "/api/accounts/me/addresses");
        nanos.addAndGet(PROPERTIES.cacheTtl().toNanos() + 1);
        preHandle("GET", "/api/accounts/me/addresses");

        verify(repository, times(2)).existsByKeycloakId(keycloakId);
    }

    private boolean preHandle(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    private void authenticate(String role) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", keycloakId.toString()));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role))));
    }
}
