package br.com.ateliedigital.outbox.worker;

import br.com.ateliedigital.outbox.OutboxEventHandler;
import br.com.ateliedigital.outbox.service.ClaimedEvent;
import br.com.ateliedigital.outbox.service.OutboxClaimService;
import br.com.ateliedigital.outbox.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(
        prefix = "atelie.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
// coordena o processamento dos eventos.
public class OutboxEventProcessor {
    private static final Logger log =
            LoggerFactory.getLogger(OutboxEventProcessor.class);

    private static final int MAX_EVENTS_PER_RUN = 20;

    private final OutboxClaimService claimService;
    private final OutboxService outboxService;
    private final Map<String, OutboxEventHandler> handlers;

    public OutboxEventProcessor(
            OutboxClaimService claimService,
            OutboxService outboxService,
            List<OutboxEventHandler> handlers) {

        this.claimService = claimService;
        this.outboxService = outboxService;

        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(
                        OutboxEventHandler::eventType,
                        Function.identity()
                ));
    }

    @Scheduled(
            fixedDelayString = "${atelie.outbox.poll-interval-ms:5000}"
    )
    public void processPending() {

        for (int i = 0; i < MAX_EVENTS_PER_RUN; i++) {

            Optional<ClaimedEvent> claimed = claimService.claimNext();

            if (claimed.isEmpty()) {
                return;
            }

            process(claimed.get());
        }
    }

    private void process(ClaimedEvent event) {

        try {
            OutboxEventHandler handler = handlers.get(event.eventType());

            if (handler == null) {
                throw new IllegalStateException(
                        "No handler registered for event type: "
                                + event.eventType()
                );
            }

            handler.handle(event.payload());

            outboxService.markProcessed(event.id());

            log.info(
                    "Outbox event processed successfully. eventId={}, type={}",
                    event.id(),
                    event.eventType()
            );

        } catch (Exception exception) {

            log.error(
                    "Failed to process outbox event. eventId={}, type={}",
                    event.id(),
                    event.eventType(),
                    exception
            );

            recordFailure(event, exception);
        }
    }

    private void recordFailure(ClaimedEvent event, Exception cause) {

        try {
            outboxService.markFailed(event.id(), cause.getMessage());

        } catch (Exception exception) {

            log.error(
                    "Failed to record outbox event failure. eventId={}",
                    event.id(),
                    exception
            );
        }
    }
}
