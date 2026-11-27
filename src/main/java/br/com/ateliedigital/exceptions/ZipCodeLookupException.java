package br.com.ateliedigital.exceptions;

public class ZipCodeLookupException extends RuntimeException {
    public ZipCodeLookupException(String message) {
        super(message);
    }

    public ZipCodeLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
