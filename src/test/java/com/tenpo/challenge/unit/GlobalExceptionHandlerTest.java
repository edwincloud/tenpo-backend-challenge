package com.tenpo.challenge.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenpo.challenge.domain.exception.ExternalServiceUnavailableException;
import com.tenpo.challenge.infrastructure.web.handler.GlobalExceptionHandler;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(CircuitBreakerRegistry.ofDefaults());

    @Test
    void servicioExternoNoDisponible_mapeaA503ConMensajeDescriptivoYRetryAfter() {
        ExternalServiceUnavailableException ex = new ExternalServiceUnavailableException(
                "No fue posible obtener el porcentaje", new RuntimeException());

        ResponseEntity<ProblemDetail> response = handler.handleExternalServiceDown(ex);
        ProblemDetail problem = response.getBody();

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getDetail()).isEqualTo("No fue posible obtener el porcentaje");
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    void circuitBreakerAbierto_mapeaA503ConRetryAfter() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("test");
        CallNotPermittedException ex = CallNotPermittedException.createCallNotPermittedException(cb);

        ResponseEntity<ProblemDetail> response = handler.handleCircuitBreakerOpen(ex);
        ProblemDetail problem = response.getBody();

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }

    @Test
    void responseStatusException_conservaElStatusOriginal() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "caído");

        ProblemDetail problem = handler.handleResponseStatus(ex);

        assertThat(problem.getStatus()).isEqualTo(503);
        assertThat(problem.getDetail()).isEqualTo("caído");
    }

    @Test
    void serverWebInput_mapeaA400ConMensajeLimpio() {
        ProblemDetail problem = handler.handleServerWebInput(new ServerWebInputException("num1 inválido"));

        assertThat(problem.getStatus()).isEqualTo(400);
        // getReason(), no getMessage(): no debe arrastrar "400 BAD_REQUEST" en el texto.
        assertThat(problem.getDetail()).doesNotContain("BAD_REQUEST");
        assertThat(problem.getDetail()).contains("num1 inválido");
    }

    @Test
    void constraintViolation_mapeaA400() {
        ConstraintViolationException ex = new ConstraintViolationException("num2 inválido", Set.of());

        ProblemDetail problem = handler.handleConstraintViolation(ex);

        assertThat(problem.getStatus()).isEqualTo(400);
    }

    @Test
    void errorNoControlado_mapeaA500SinExponerDetallesInternos() {
        ProblemDetail problem = handler.handleUnexpected(new RuntimeException("stacktrace sensible con detalles internos"));

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getDetail()).doesNotContain("stacktrace sensible");
    }
}
