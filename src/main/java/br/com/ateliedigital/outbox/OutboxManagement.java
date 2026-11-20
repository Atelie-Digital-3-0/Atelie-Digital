package br.com.ateliedigital.outbox;

import org.springframework.stereotype.Service;
import br.com.ateliedigital.outbox.model.OutboxEvent;
import br.com.ateliedigital.outbox.repository.OutboxEventRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class OutboxManagement {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxManagement(
            OutboxEventRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Object payload) {

        OutboxEvent event = new OutboxEvent();

        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(objectMapper.valueToTree(payload));

        repository.save(event);
    }
}
