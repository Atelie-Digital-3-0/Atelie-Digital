package br.com.ateliedigital.outbox;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/** Implementado pelos módulos que querem reagir a um tipo de evento da Outbox. Deve ser idempotente. */
public interface OutboxEventHandler {
    String eventType();

    void handle(Map<String, Object> payload);
}
