package br.com.ateliedigital.exceptions;

import tools.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Objects;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, BindException.class})
    public ResponseEntity<ApiError> badRequest(Exception exception, HttpServletRequest request) {
        String message = exception instanceof MethodArgumentNotValidException invalid
                ? invalid.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage()).filter(Objects::nonNull).findFirst().orElse("Invalid datas")
                : exception.getMessage();
        if (exception instanceof HttpMessageNotReadableException unreadable) {
            message = unreadableMessage(unreadable);
        }
        return response(HttpStatus.BAD_REQUEST, message, request);
    }

    private String unreadableMessage(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidFormatException invalid && invalid.getTargetType() != null
                    && java.time.temporal.TemporalAccessor.class.isAssignableFrom(invalid.getTargetType())) {
                String field = invalid.getPath().isEmpty() ? "date" : invalid.getPath().getLast().getPropertyName();
                return field + " must be a valid date in the format yyyy-MM-dd";
            }
        }
        return "Malformed or unreadable request body";
    }

    @ExceptionHandler({ResourceNotFoundException.class, EntityNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> notFound(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({RuleConflit.class, VioletedRule.class, DatabaseException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> conflict(Exception exception, HttpServletRequest request) {
        String message = exception instanceof DataIntegrityViolationException
                ? "Violation of integrity rule or data duplication" : exception.getMessage();
        return response(HttpStatus.CONFLICT, message, request);
    }

    /**
     * Negado por {@code @PreAuthorize}: a excecao e lancada dentro do controller, portanto chega aqui antes do
     * {@code ExceptionTranslationFilter}. Sem este handler cairia em {@link #unexpected} e viraria 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        return response(status, exception.getReason() != null ? exception.getReason() : status.getReasonPhrase(), request);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ApiError> externalService(RestClientException exception, HttpServletRequest request) {
        log.error("External service call failed on {}", request.getRequestURI(), exception);
        return response(HttpStatus.BAD_GATEWAY, "External service unavailable or rejected the request", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error when processing the request", request);
    }
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<ApiError> storageError(
            StorageException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Storage operation failed on {}",
                request.getRequestURI(),
                exception
        );

        return response(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Storage service is temporarily unavailable",
                request
        );
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), message, request.getRequestURI()));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> illegalArgument(
            IllegalArgumentException exception,
            HttpServletRequest request
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(ZipCodeLookupException.class)
    public ResponseEntity<ApiError> zipCodeLookup(
            ZipCodeLookupException exception,
            HttpServletRequest request
    ) {
        log.error(
                "ViaCEP lookup failed on {}",
                request.getRequestURI(),
                exception
        );

        return response(
                HttpStatus.BAD_GATEWAY,
                "Unable to retrieve address information from ViaCEP",
                request
        );
    }
}
