package br.com.ateliedigital.accounts.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuracao do cache de contas existentes usado pelo {@link AccountCompletionInterceptor}
 * (prefixo {@code atelie.account-completion} em {@code application.yml}).
 *
 * @param cacheTtl     por quanto tempo uma conta confirmada dispensa nova consulta ao banco
 * @param cacheMaxSize maximo de contas em cache; {@code 0} desativa o cache (uma consulta por requisicao)
 */
@ConfigurationProperties(prefix = "atelie.account-completion")
public record AccountCompletionProperties(
        @DefaultValue("5m") Duration cacheTtl,
        @DefaultValue("10000") long cacheMaxSize) {
}
