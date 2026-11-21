package br.com.ateliedigital.storage.dto;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record StoredObject(
        InputStream inputStream,
        long size,
        String contentType
) implements AutoCloseable {
    public StoredObject {
        Objects.requireNonNull(inputStream, "inputStream cannot be null");
        Objects.requireNonNull(contentType, "contentType cannot be null");

        if (size < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }
    }
    @Override
    public void close() throws Exception {
        inputStream.close();
    }
}
