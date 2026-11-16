package br.com.ateliedigital.accounts.integration.viacep;

import br.com.ateliedigital.accounts.dto.AddressResponses.ZipCodeResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ViaCepClient {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ViaCepClient.class);
    private final RestClient restClient;

    public ViaCepClient(RestClient.Builder builder,
                        @Value("${atelie.viacep.base-url:https://viacep.com.br}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public ZipCodeResponse lookup(String rawCep) {
        String cep = rawCep.replaceAll("\\D", "");
        if (!cep.matches("[0-9]{8}")) {
            return new ZipCodeResponse(rawCep, null, null, null, null, null, false);
        }
        try {
            ViaCepResult result = restClient.get().uri("/ws/{cep}/json/", cep)
                    .retrieve().body(ViaCepResult.class);
            if (result == null || Boolean.TRUE.equals(result.erro())) {
                return new ZipCodeResponse(cep, null, null, null, null, null, false);
            }
            return new ZipCodeResponse(result.cep(), result.logradouro(), result.complemento(),
                    result.bairro(), result.localidade(), result.uf(), true);
        } catch (RuntimeException exception) {
            log.warn("Falha ao consultar o ViaCEP para o CEP {}: {}", cep, exception.toString());
            return new ZipCodeResponse(cep, null, null, null, null, null, false);
        }
    }

    private record ViaCepResult(String cep, String logradouro, String complemento, String bairro,
                                String localidade, String uf, Boolean erro) { }
}
