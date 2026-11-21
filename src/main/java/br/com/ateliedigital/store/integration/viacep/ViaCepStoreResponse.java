package br.com.ateliedigital.store.integration.viacep;

public record ViaCepStoreResponse(
        String cep,
        String logradouro,
        String complemento,
        String bairro,
        String localidade,
        String uf,
        Boolean erro
) {
}
