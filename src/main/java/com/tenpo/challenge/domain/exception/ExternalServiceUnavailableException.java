package com.tenpo.challenge.domain.exception;

/**
 * Salta cuando el servicio externo de porcentaje falla después de agotar los
 * reintentos (o cuando el circuit breaker ya está abierto). El GlobalExceptionHandler
 * la convierte en un 503.
 */
public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
