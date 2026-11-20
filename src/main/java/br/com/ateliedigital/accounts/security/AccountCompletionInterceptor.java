package br.com.ateliedigital.accounts.security;

import br.com.ateliedigital.accounts.repository.AccountRepository;
import br.com.ateliedigital.accounts.service.AccountService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.UUID;

/**
 * Garante que nenhum usuario autenticado use a API sem ter a conta completa no banco local.
 * <p>
 * Quem se cadastra pelo Keycloak ou entra com Google ja possui token valido, mas ainda nao possui linha em
 * {@code accounts.accounts}. Enquanto isso, toda rota protegida responde {@code 403} com a mensagem
 * {@value #ERROR_CODE}; o front deve entao levar o usuario ao formulario de
 * {@code PUT /api/accounts/me/complete}.
 * <p>
 * Ficam de fora: requisicoes anonimas, usuarios {@code ADMIN} (nao possuem conta de cliente) e as rotas
 * necessarias para completar o cadastro ou publicas por definicao em {@code SecurityConfiguration}.
 * <p>
 * Para evitar uma consulta por requisicao, contas ja confirmadas ficam em cache local por
 * {@code atelie.account-completion.cache-ttl}. Apenas a existencia e cacheada, nunca a ausencia.
 */
@Component
public class AccountCompletionInterceptor implements HandlerInterceptor {
    /** Valor de {@code message} no corpo do erro 403; contrato estavel para o front-end. */
    public static final String ERROR_CODE = "ACCOUNT_INCOMPLETE";

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private record Route(String method, String pattern) {
        boolean matches(HttpServletRequest request, String path) {
            return method.equals(request.getMethod()) && MATCHER.match(pattern, path);
        }
    }

    private static final List<Route> EXEMPT_ROUTES = List.of(
            // Necessarias para o usuario completar o cadastro (a consulta de CEP preenche o formulario).
            new Route("GET", "/api/accounts/me"),
            new Route("PUT", "/api/accounts/me/complete"),
            new Route("GET", "/api/accounts/me/addresses/cep/*"),
            // Publicas em SecurityConfiguration: manter alinhado com la.
            new Route("POST", "/api/accounts"),
            new Route("GET", "/api/home/**"),
            new Route("GET", "/api/products/**"),
            new Route("GET", "/api/stores/**")
    );

    private final AccountRepository accountRepository;
    /**
     * Guarda apenas contas que ja existem. A ausencia nunca e cacheada: assim que o usuario conclui
     * {@code PUT /api/accounts/me/complete}, a proxima requisicao ja e liberada.
     */
    private final Cache<UUID, Boolean> existingAccounts;
    private final boolean cacheEnabled;

    @Autowired
    public AccountCompletionInterceptor(AccountRepository accountRepository, AccountCompletionProperties properties) {
        this(accountRepository, properties, Ticker.systemTicker());
    }

    AccountCompletionInterceptor(AccountRepository accountRepository, AccountCompletionProperties properties,
                                 Ticker ticker) {
        this.accountRepository = accountRepository;
        // maximumSize(0) remove entradas de forma assincrona; desativar explicitamente e deterministico.
        this.cacheEnabled = properties.cacheMaxSize() > 0;
        this.existingAccounts = Caffeine.newBuilder()
                .maximumSize(properties.cacheMaxSize())
                .expireAfterWrite(properties.cacheTtl())
                .ticker(ticker)
                .build();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || isAdmin(authentication)
                || isExempt(request)) {
            return true;
        }
        if (!accountExists(AccountService.keycloakId(jwtAuthentication))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ERROR_CODE);
        }
        return true;
    }

    private boolean accountExists(UUID keycloakId) {
        if (!cacheEnabled) {
            return accountRepository.existsByKeycloakId(keycloakId);
        }
        if (existingAccounts.getIfPresent(keycloakId) != null) {
            return true;
        }
        boolean exists = accountRepository.existsByKeycloakId(keycloakId);
        if (exists) {
            existingAccounts.put(keycloakId, Boolean.TRUE);
        }
        return exists;
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private static boolean isExempt(HttpServletRequest request) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }
        String path = request.getServletPath();
        return EXEMPT_ROUTES.stream().anyMatch(route -> route.matches(request, path));
    }
}
