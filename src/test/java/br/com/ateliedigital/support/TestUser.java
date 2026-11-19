package br.com.ateliedigital.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Usuario de teste com identidade unica (token, e-mail e CPF): cada teste cria o seu para nao depender
 * de ordem de execucao nem de limpeza do banco.
 */
public record TestUser(UUID keycloakId, String email, String cpf) {

    public static TestUser random() {
        UUID keycloakId = UUID.randomUUID();
        return new TestUser(keycloakId, "user." + keycloakId + "@teste.dev", randomValidCpf());
    }

    /** Token para chamar os services diretamente (sem passar pela camada HTTP). */
    public JwtAuthenticationToken token() {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"),
                Map.of("sub", keycloakId.toString(), "email", email));
        return new JwtAuthenticationToken(jwt);
    }

    /** Requisicao autenticada com o papel CLIENT. */
    public RequestPostProcessor client() {
        return withRole("CLIENT");
    }

    /** Requisicao autenticada com o papel informado (sem o prefixo {@code ROLE_}). */
    public RequestPostProcessor withRole(String role) {
        RequestPostProcessor authentication = jwt()
                .jwt(jwt -> jwt.subject(keycloakId.toString()).claim("email", email))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
        return request -> {
            // O MockMvc nao preenche o servletPath (num container real o DispatcherServlet mapeado em "/" o recebe
            // igual ao caminho); o AccountCompletionInterceptor usa esse valor para reconhecer as rotas isentas.
            request.setServletPath(request.getRequestURI());
            return authentication.postProcessRequest(request);
        };
    }

    public static String randomValidCpf() {
        StringBuilder cpf = new StringBuilder();
        for (int i = 0; i < 9; i++) cpf.append(ThreadLocalRandom.current().nextInt(10));
        if (cpf.chars().distinct().count() == 1) return randomValidCpf();
        for (int length = 9; length <= 10; length++) {
            int sum = 0;
            for (int i = 0; i < length; i++) sum += (cpf.charAt(i) - '0') * (length + 1 - i);
            int digit = sum * 10 % 11;
            cpf.append(digit == 10 ? 0 : digit);
        }
        return cpf.toString();
    }
}
