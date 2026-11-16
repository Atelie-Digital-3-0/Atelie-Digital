package br.com.ateliedigital.accounts;

import br.com.ateliedigital.accounts.service.AccountService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * API publica do modulo accounts para os demais modulos (os subpacotes sao internos ao modulo).
 */
@Service
public class AccountManagement {
    private final AccountService accountService;

    public AccountManagement(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Torna o usuario um artesao (papel ARTISAN no Keycloak). Deve ser chamado pelo modulo store
     * quando o usuario cadastrar uma loja, de preferencia apos o commit do cadastro.
     * <p>
     * Idempotente: se o usuario ja for ARTISAN nada e alterado. Exige que a conta ja exista (404 caso contrario).
     * O papel passa a constar no token a partir da proxima renovacao do token pelo front-end.
     *
     * @param keycloakId {@code sub} do JWT do dono da loja
     */
    public void grantArtisanRole(UUID keycloakId) {
        accountService.grantArtisanRole(keycloakId);
    }
}
