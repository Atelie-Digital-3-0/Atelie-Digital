package br.com.ateliedigital.outbox.service;


import java.util.Map;
import java.util.UUID;

//objeto para transportar os dados de um evento reservado pelo processamento
public record ClaimedEvent(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        Map<String, Object> payload,
        int attempts,
        int maxAttempts
) {
}
