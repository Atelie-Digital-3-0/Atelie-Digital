package br.com.ateliedigital.outbox.service;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

//objeto para transportar os dados de um evento reservado pelo processamento
public record ClaimedEvent(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        JsonNode payload,
        int attempts,
        int maxAttempts
) {
}
