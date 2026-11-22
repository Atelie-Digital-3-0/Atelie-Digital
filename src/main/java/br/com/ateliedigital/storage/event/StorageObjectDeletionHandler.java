package br.com.ateliedigital.storage.event;

import br.com.ateliedigital.outbox.OutboxEventHandler;
import br.com.ateliedigital.storage.service.StorageService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class StorageObjectDeletionHandler implements OutboxEventHandler {

    public static final String EVENT_TYPE =
            "STORAGE_OBJECT_DELETION_REQUESTED";

    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    public StorageObjectDeletionHandler(
            StorageService storageService,
            ObjectMapper objectMapper
    ) {
        this.storageService = storageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException(
                    "Storage object deletion payload cannot be null"
            );
        }

        StorageObjectDeletionPayload event = objectMapper.convertValue(
                payload,
                StorageObjectDeletionPayload.class
        );

        if (event.objectKey() == null || event.objectKey().isBlank()) {
            throw new IllegalArgumentException(
                    "Object key cannot be null or blank"
            );
        }

        storageService.delete(event.objectKey());
    }
}
