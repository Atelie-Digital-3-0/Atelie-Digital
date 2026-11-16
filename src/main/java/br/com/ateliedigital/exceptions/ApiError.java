package br.com.ateliedigital.exceptions;
import java.time.Instant;

public record ApiError(Instant timestamp, int status, String message, String path) {}