package br.com.ateliedigital.accounts.event;

import java.time.Instant;
import java.util.UUID;

public record AccountCreatedEvent(UUID id, String name, String email, Instant timestamp) { }
