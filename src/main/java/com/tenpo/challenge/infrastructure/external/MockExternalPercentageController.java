package com.tenpo.challenge.infrastructure.external;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Simula el "servicio externo" del enunciado: devuelve un porcentaje fijo (10% por
 * defecto) que después se suma al cálculo de num1+num2.
 *
 * También sabe fallar a propósito, para poder probar el retry y el circuit breaker sin
 * depender de un proveedor real:
 *  - ?fail=true fuerza un error 503 (útil para probar manual desde Postman).
 *  - failure-rate en application.yml simula fallas aleatorias, más de tanto en tanto.
 */
@RestController
public class MockExternalPercentageController {

    private static final Logger log = LoggerFactory.getLogger(MockExternalPercentageController.class);

    private final BigDecimal fixedPercentage;
    private final double failureRate;

    public MockExternalPercentageController(
            @Value("${external.percentage-service.fixed-percentage:10}") BigDecimal fixedPercentage,
            @Value("${external.percentage-service.failure-rate:0}") double failureRate) {
        this.fixedPercentage = fixedPercentage;
        this.failureRate = failureRate;
    }

    @GetMapping("/mock/external/percentage")
    public Mono<PercentageResponse> getPercentage(@RequestParam(name = "fail", required = false) Boolean forceFail) {
        boolean shouldFail = Boolean.TRUE.equals(forceFail) || ThreadLocalRandom.current().nextDouble() < failureRate;
        if (shouldFail) {
            log.warn("Simulando fallo del servicio externo de porcentaje");
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servicio externo simulando caída"));
        }
        return Mono.just(new PercentageResponse(fixedPercentage));
    }
}
