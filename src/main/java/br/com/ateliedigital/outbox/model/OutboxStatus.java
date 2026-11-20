package br.com.ateliedigital.outbox.model;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    DEAD
}
