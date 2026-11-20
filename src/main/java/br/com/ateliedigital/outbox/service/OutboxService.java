package br.com.ateliedigital.outbox.service;

import org.springframework.stereotype.Service;
import br.com.ateliedigital.outbox.model.OutboxEvent;
import br.com.ateliedigital.outbox.model.OutboxStatus;
import br.com.ateliedigital.outbox.repository.OutboxEventRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

// controla o ciclo de vida dos eventos após a reserva.
@Service
public class OutboxService {
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final long INITIAL_BACKOFF_SECONDS = 30;
    private static final long MAX_BACKOFF_SECONDS = 3600;

    private final OutboxEventRepository repository;

    public OutboxService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markProcessed(UUID eventId) {
        OutboxEvent event = findEvent(eventId);

        event.setStatus(OutboxStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        event.setLockedUntil(null);
        event.setLastError(null);
    }

    @Transactional
    public void markFailed(UUID eventId, String error) {
        OutboxEvent event = findEvent(eventId);

        event.setLockedUntil(null);
        event.setLastError(truncateError(error));

        if (event.getAttempts() >= event.getMaxAttempts()) {
            event.setStatus(OutboxStatus.DEAD);
            return;
        }

        event.setStatus(OutboxStatus.PENDING);
        event.setNextAttemptAt(
                Instant.now().plusSeconds(calculateBackoff(event.getAttempts()))
        );
    }

    private OutboxEvent findEvent(UUID eventId) {
        return repository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox event not found: " + eventId
                ));
    }

    private static long calculateBackoff(int attempts) {
        int exponent = Math.max(0, attempts - 1);

        long backoff = INITIAL_BACKOFF_SECONDS * (1L << Math.min(exponent, 7));

        return Math.min(backoff, MAX_BACKOFF_SECONDS);
    }

    private static String truncateError(String error) {
        if (error == null) {
            return "Unknown processing error";
        }

        return error.length() <= MAX_ERROR_LENGTH
                ? error
                : error.substring(0, MAX_ERROR_LENGTH);
    }

}
