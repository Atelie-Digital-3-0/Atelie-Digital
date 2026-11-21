package br.com.ateliedigital.store.integration.viacep;

public class ZipCodeLookupException extends RuntimeException {
    public ZipCodeLookupException(String message) {
        super(message);
    }

    public ZipCodeLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
