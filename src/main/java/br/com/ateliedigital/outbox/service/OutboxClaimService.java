package br.com.ateliedigital.outbox.service;

import br.com.ateliedigital.outbox.model.OutboxEvent;
import br.com.ateliedigital.outbox.model.OutboxStatus;
import br.com.ateliedigital.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

//o mecanismo de reserva.
@Service
public class OutboxClaimService {
    private static final Duration LEASE = Duration.ofMinutes(5);

    private final OutboxEventRepository repository;

    public OutboxClaimService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Optional<ClaimedEvent> claimNext() {

        Instant now = Instant.now();

        return repository.findClaimable(now, 1)
                .stream()
                .findFirst()
                .map(this::claim);
    }

    private ClaimedEvent claim(OutboxEvent event) {

        event.setStatus(OutboxStatus.PROCESSING);
        event.setAttempts(event.getAttempts() + 1);
        event.setLockedUntil(Instant.now().plus(LEASE));

        return new ClaimedEvent(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload(),
                event.getAttempts(),
                event.getMaxAttempts()
        );
    }
}
