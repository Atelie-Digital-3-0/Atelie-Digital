package br.com.ateliedigital.outbox.integration;


import br.com.ateliedigital.outbox.model.OutboxEvent;
import br.com.ateliedigital.outbox.model.OutboxStatus;
import br.com.ateliedigital.outbox.repository.OutboxEventRepository;
import br.com.ateliedigital.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class OutboxEventRepositoryIntegrationTest extends PostgresIntegrationTest{



    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAllInBatch();
    }

    @Test
    void shouldPersistOutboxEventWithDefaultValues() throws Exception {
        OutboxEvent event = createEvent(
                OutboxStatus.PENDING,
                Instant.now()
        );

        OutboxEvent saved = repository.saveAndFlush(event);

        entityManager.clear();

        OutboxEvent persisted = repository.findById(saved.getId())
                .orElseThrow();

        assertNotNull(persisted.getId());
        assertEquals("ACCOUNT", persisted.getAggregateType());
        assertEquals("ACCOUNT_CREATED", persisted.getEventType());
        assertEquals(OutboxStatus.PENDING, persisted.getStatus());

        assertEquals(0, persisted.getAttempts());
        assertEquals(10, persisted.getMaxAttempts());

        assertNotNull(persisted.getCreatedAt());
        assertNotNull(persisted.getNextAttemptAt());

        assertEquals(
                Map.of(
                        "accountId", "123",
                        "email", "test@example.com"
                ),
                persisted.getPayload()
        );
    }

    @Test
    void shouldFindOnlyClaimableEvents() {
        Instant now = Instant.now();

        OutboxEvent pendingEvent = createEvent(
                OutboxStatus.PENDING,
                now.minusSeconds(60)
        );

        OutboxEvent expiredEvent = createEvent(
                OutboxStatus.PROCESSING,
                now.minusSeconds(60)
        );
        expiredEvent.setLockedUntil(now.minusSeconds(30));

        OutboxEvent scheduledEvent = createEvent(
                OutboxStatus.PENDING,
                now.plusSeconds(300)
        );

        OutboxEvent lockedEvent = createEvent(
                OutboxStatus.PROCESSING,
                now
        );
        lockedEvent.setLockedUntil(now.plusSeconds(300));

        OutboxEvent processedEvent = createEvent(
                OutboxStatus.PROCESSED,
                now
        );

        repository.saveAllAndFlush(List.of(
                pendingEvent,
                expiredEvent,
                scheduledEvent,
                lockedEvent,
                processedEvent
        ));

        List<OutboxEvent> claimableEvents =
                repository.findClaimable(now, 10);

        Set<UUID> returnedIds = claimableEvents.stream()
                .map(OutboxEvent::getId)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(pendingEvent.getId(), expiredEvent.getId()),
                returnedIds
        );
    }

    private OutboxEvent createEvent(
            OutboxStatus status,
            Instant nextAttemptAt
    ) {
        OutboxEvent event = new OutboxEvent();

        event.setAggregateType("ACCOUNT");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("ACCOUNT_CREATED");
        event.setPayload(createPayload());
        event.setStatus(status);
        event.setAttempts(0);
        event.setMaxAttempts(10);
        event.setNextAttemptAt(nextAttemptAt);

        return event;
    }

    private Map<String, Object> createPayload() {
        return Map.of(
                "accountId", "123",
                "email", "test@example.com"
        );
    }
}
