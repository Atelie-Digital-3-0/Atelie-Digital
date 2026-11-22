package br.com.ateliedigital.store.dto.responses;

import java.time.LocalDateTime;
import java.util.UUID;


public class StoreResponses {
    public record StorePublicResponse(
            UUID id,
            UUID artisanId,
            String name,
            String phone,
            String email,
            String description,
            CategoryResponse category,
            String image,
            String banner,
            StoreAddressResponses.AddressResponse address,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
            // TODO: Adicionar List<ProductResponse> products aqui quando o módulo de produtos estiver pronto.
    ) {}

    public record CategoryResponse(
            UUID id,
            String name
    ) {}
}
