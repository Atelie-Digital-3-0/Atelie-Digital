package br.com.ateliedigital.outbox.service;

import br.com.ateliedigital.outbox.model.OutboxEvent;
import br.com.ateliedigital.outbox.model.OutboxStatus;
import br.com.ateliedigital.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OutboxServiceTest {

    @Mock
    private OutboxEventRepository repository;

    @InjectMocks
    private OutboxService outboxService;

    private OutboxEvent event;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        event = new OutboxEvent();

        event.setId(eventId);
        event.setStatus(OutboxStatus.PROCESSING);
        event.setAttempts(1);
        event.setMaxAttempts(3);
        event.setLockedUntil(Instant.now().plusSeconds(60));
    }

    @Test
    void shouldMarkEventAsProcessed() {
        when(repository.findById(eventId)).thenReturn(Optional.of(event));

        outboxService.markProcessed(eventId);

        assertEquals(OutboxStatus.PROCESSED, event.getStatus());
        assertNotNull(event.getProcessedAt());
        assertNull(event.getLockedUntil());
        assertNull(event.getLastError());

        verify(repository).findById(eventId);
    }

    @Test
    void shouldReturnEventToPendingWhenProcessingFails() {
        String error = "Processing failed";

        when(repository.findById(eventId)).thenReturn(Optional.of(event));

        outboxService.markFailed(eventId, error);

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(error, event.getLastError());
        assertNull(event.getLockedUntil());
        assertNotNull(event.getNextAttemptAt());

        verify(repository).findById(eventId);
    }

    @Test
    void shouldMarkEventAsDeadWhenMaxAttemptsReached() {
        event.setAttempts(3);

        when(repository.findById(eventId)).thenReturn(Optional.of(event));

        outboxService.markFailed(eventId, "Processing failed");

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertEquals("Processing failed", event.getLastError());
        assertNull(event.getLockedUntil());

        verify(repository).findById(eventId);
    }
}
