package br.com.ateliedigital.accounts.event;

import java.time.Instant;
import java.util.UUID;

public record SensitiveAccountUpdatedEvent(UUID id, String field, String oldValue, String newValue,
                                           Instant timestamp) { }
