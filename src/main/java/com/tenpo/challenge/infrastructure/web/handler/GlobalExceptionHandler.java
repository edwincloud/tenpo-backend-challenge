package com.tenpo.challenge.infrastructure.web.handler;

import com.tenpo.challenge.domain.exception.ExternalServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

/**
 * Manejo centralizado de errores (requisito 5): acá se traduce cualquier excepción de
 * la API a una respuesta HTTP consistente con ProblemDetail (RFC 7807), con un mensaje
 * claro para que el cliente entienda qué pasó.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String RESILIENCE_INSTANCE = "percentageService";

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public GlobalExceptionHandler(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    // Errores de validación de entrada (ej: num1 nulo, JSON mal formado) -> 400
    @ExceptionHandler(WebExchangeBindException.class)
    public ProblemDetail handleValidation(WebExchangeBindException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "La solicitud contiene datos inválidos");
        problem.setTitle("Validación fallida");
        problem.setType(URI.create("about:blank"));
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ex.getMessage());
        problem.setProperty("errors", errors);
        return problem;
    }

    // HttpStatusResolver.resolveMessage() saca el "400 BAD_REQUEST \"...\"" que trae
    // ex.getMessage() para este tipo de excepción; es la misma lógica que usa
    // CallHistoryAspect, para que el historial y la respuesta HTTP muestren lo mismo.
    @ExceptionHandler(ServerWebInputException.class)
    public ProblemDetail handleServerWebInput(ServerWebInputException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "No fue posible interpretar la solicitud: " + HttpStatusResolver.resolveMessage(ex));
        problem.setTitle("Solicitud inválida");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "No fue posible interpretar la solicitud: " + ex.getMessage());
        problem.setTitle("Solicitud inválida");
        return problem;
    }

    // El servicio externo de porcentaje no respondió tras los reintentos configurados -> 503
    @ExceptionHandler(ExternalServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleExternalServiceDown(ExternalServiceUnavailableException ex) {
        log.error("Servicio externo no disponible", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Servicio externo no disponible");
        return withRetryAfter(problem);
    }

    // El circuit breaker está abierto: ni siquiera se intentó llamar al servicio externo -> 503
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCircuitBreakerOpen(CallNotPermittedException ex) {
        log.warn("Circuit breaker abierto: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio externo de porcentaje está temporalmente deshabilitado (circuit breaker abierto). Intenta más tarde.");
        problem.setTitle("Servicio temporalmente no disponible");
        return withRetryAfter(problem);
    }

    // Retry-After: mejor decirle al cliente cuánto esperar que dejarlo adivinar. Reutilizo
    // el wait-duration-in-open-state del circuit breaker, porque es la misma ventana de
    // protección del servicio externo, ya sea que el breaker esté abierto o que recién haya
    // fallado tras agotar los reintentos.
    private ResponseEntity<ProblemDetail> withRetryAfter(ProblemDetail problem) {
        long waitMillis = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE)
                .getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1);
        long retryAfterSeconds = Duration.ofMillis(waitMillis).toSeconds();
        return ResponseEntity.status(problem.getStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(problem);
    }

    // Excepciones lanzadas explícitamente con un status HTTP concreto (ej: el mock externo simulando 503)
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        problem.setTitle(ex.getStatusCode().toString());
        return problem;
    }

    // Cualquier otro error no controlado -> 500, sin filtrar detalles internos al cliente
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Si el problema persiste, contacta al equipo de soporte.");
        problem.setTitle("Error interno");
        return problem;
    }
}
