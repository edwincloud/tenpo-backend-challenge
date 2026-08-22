package com.tenpo.challenge.unit;

import static org.mockito.Mockito.when;

import com.tenpo.challenge.application.CalculationService;
import com.tenpo.challenge.domain.exception.ExternalServiceUnavailableException;
import com.tenpo.challenge.domain.model.CalculationResult;
import com.tenpo.challenge.infrastructure.web.CalculationController;
import com.tenpo.challenge.infrastructure.web.handler.GlobalExceptionHandler;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Test de "slice" web: monto el controller real (sin AOP, ver nota abajo) sobre un
 * WebTestClient, sin levantar el contexto completo de Spring, para validar el contrato
 * HTTP (status codes, payloads) a nivel de controller.
 *
 * Nota: WebTestClient.bindToController no aplica proxies de AOP, por eso el registro de
 * historial vía CallHistoryAspect se prueba aparte en CallHistoryAspectTest.
 */
@ExtendWith(MockitoExtension.class)
class CalculationControllerTest {

    @Mock
    private CalculationService calculationService;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        webTestClient = WebTestClient.bindToController(new CalculationController(calculationService, validator))
                .controllerAdvice(new GlobalExceptionHandler(CircuitBreakerRegistry.ofDefaults()))
                .build();
    }

    @Test
    void calculate_devuelve200ConElResultado() {
        when(calculationService.calculate(BigDecimal.valueOf(5), BigDecimal.valueOf(5)))
                .thenReturn(Mono.just(CalculationResult.of(BigDecimal.valueOf(5), BigDecimal.valueOf(5), BigDecimal.TEN)));

        webTestClient.post().uri("/api/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"num1": 5, "num2": 5}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result").isEqualTo(11.0);
    }

    @Test
    void calculate_conServicioExternoCaido_devuelve503() {
        when(calculationService.calculate(BigDecimal.ONE, BigDecimal.ONE))
                .thenReturn(Mono.error(new ExternalServiceUnavailableException("caído", new RuntimeException())));

        webTestClient.post().uri("/api/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"num1": 1, "num2": 1}
                        """)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void calculate_sinNum1_devuelve400() {
        webTestClient.post().uri("/api/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"num2": 5}
                        """)
                .exchange()
                .expectStatus().isEqualTo(400);
    }
}
