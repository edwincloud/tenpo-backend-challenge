package com.tenpo.challenge.infrastructure.web.handler;

import com.tenpo.challenge.domain.exception.ExternalServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

/**
 * Traduce una excepción a un código HTTP. La usan tanto GlobalExceptionHandler (para armar
 * la respuesta de error) como CallHistoryAspect (para que el historial quede con el mismo
 * status code que de verdad se le devolvió al cliente).
 */
public final class HttpStatusResolver {

    private HttpStatusResolver() {
    }

    public static int resolve(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return rse.getStatusCode().value();
        }
        if (ex instanceof ServerWebInputException) {
            return HttpStatus.BAD_REQUEST.value();
        }
        if (ex instanceof RequestNotPermitted) {
            return HttpStatus.TOO_MANY_REQUESTS.value();
        }
        if (ex instanceof CallNotPermittedException || ex instanceof ExternalServiceUnavailableException) {
            return HttpStatus.SERVICE_UNAVAILABLE.value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    // ResponseStatusException.getMessage() trae el status metido en el texto
    // (ej. "400 BAD_REQUEST \"num1 es obligatorio\""); getReason() da el mensaje
    // limpio directamente. Lo centralizo acá para que el historial (CallHistoryAspect)
    // y la respuesta HTTP (GlobalExceptionHandler) siempre muestren el mismo texto.
    public static String resolveMessage(Throwable ex) {
        if (ex instanceof ResponseStatusException rse) {
            return rse.getReason();
        }
        return ex.getMessage();
    }
}
