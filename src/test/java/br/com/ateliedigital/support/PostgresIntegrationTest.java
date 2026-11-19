package br.com.ateliedigital.support;

import br.com.ateliedigital.accounts.event.AccountAuditPublisher;
import br.com.ateliedigital.accounts.integration.keycloak.KeycloakAdminClient;
import br.com.ateliedigital.accounts.integration.viacep.ViaCepClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base dos testes de integracao: sobe a aplicacao contra um Postgres real (Testcontainers) com as migrations do Flyway.
 * <p>
 * O container e compartilhado por todas as classes de teste (singleton) e, como a configuracao do contexto e a mesma,
 * o contexto do Spring tambem e reaproveitado entre elas. Servicos externos (Keycloak, RabbitMQ e ViaCEP) sao mockados.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean protected KeycloakAdminClient keycloak;
    @MockitoBean protected AccountAuditPublisher auditPublisher;
    @MockitoBean protected ViaCepClient viaCepClient;

    @Autowired protected MockMvc mockMvc;

    /** Conclui o cadastro do usuario ({@code PUT /api/accounts/me/complete}), criando a conta local a partir do token. */
    protected ResultActions completeRegistration(TestUser user, String addressJson) throws Exception {
        return mockMvc.perform(put("/api/accounts/me/complete")
                .with(user.client())
                .contentType(MediaType.APPLICATION_JSON)
                .content(Payloads.completeAccount(user, addressJson)));
    }
}
