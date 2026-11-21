package br.com.ateliedigital.store.event;

import java.util.UUID;

public record StoreCreatedPayload(
        UUID storeId,
        UUID keycloakId
) {
}
