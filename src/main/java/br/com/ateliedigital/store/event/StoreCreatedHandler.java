package br.com.ateliedigital.store.event;

import br.com.ateliedigital.accounts.AccountManagement;
import br.com.ateliedigital.outbox.OutboxEventHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
public class StoreCreatedHandler implements OutboxEventHandler{

    public static final String EVENT_TYPE = "STORE_CREATED";

    private final AccountManagement accountManagement;
    private final ObjectMapper objectMapper;

    public StoreCreatedHandler(
            AccountManagement accountManagement,
            ObjectMapper objectMapper
    ) {
        this.accountManagement = accountManagement;
        this.objectMapper = objectMapper;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public void handle(Map<String, Object> payload) {
        StoreCreatedPayload event = objectMapper.convertValue(
                payload,
                StoreCreatedPayload.class
        );

        if (event.storeId() == null || event.keycloakId() == null) {
            throw new IllegalArgumentException(
                    "Invalid STORE_CREATED event payload"
            );
        }

        accountManagement.grantArtisanRole(event.keycloakId());
    }
}
