package br.com.ateliedigital.store.event;

import java.util.UUID;

public record StoreCreatedEvent(UUID storeId, UUID keycloakId) {
}
