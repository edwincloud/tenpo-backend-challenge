package com.tenpo.challenge.infrastructure.external;

import com.tenpo.challenge.domain.exception.ExternalServiceUnavailableException;
import com.tenpo.challenge.domain.port.PercentageProvider;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Adaptador que implementa PercentageProvider llamando por HTTP al servicio externo.
 *
 * Sobre la resiliencia: acá compongo los operadores reactivos de Resilience4j a mano
 * (RetryOperator, CircuitBreakerOperator) en vez de usar las anotaciones @Retry/@CircuitBreaker.
 * Es a propósito: el orden en que esas anotaciones se aplican vía AOP no es tan predecible
 * ni fácil de testear, así que prefiero encadenarlos yo mismo, con el orden que realmente
 * necesito:
 *  1. Retry (el más interno): reintenta la llamada HTTP hasta 3 veces (instancia
 *     "percentageService" en application.yml).
 *  2. CircuitBreaker (el más externo): cuenta el resultado FINAL de cada llamada a
 *     getPercentage() -ya con los reintentos aplicados- como un único éxito o fallo. Así, si
 *     el servicio externo viene fallando seguido, se abre el circuito y deja de intentar
 *     (fail-fast) en vez de seguir insistiendo en vano.
 * Cualquier error que sobreviva a los dos (reintentos agotados o circuito abierto) se
 * normaliza a ExternalServiceUnavailableException: el resto de la app no necesita saber
 * nada de las excepciones propias de WebClient o Resilience4j.
 */
@Component
public class ExternalPercentageClient implements PercentageProvider {

    private static final Logger log = LoggerFactory.getLogger(ExternalPercentageClient.class);
    private static final String RESILIENCE_INSTANCE = "percentageService";

    private final WebClient webClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ExternalPercentageClient(
            WebClient.Builder webClientBuilder,
            @Value("${external.percentage-service.base-url}") String baseUrl,
            RetryRegistry retryRegistry,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.retry = retryRegistry.retry(RESILIENCE_INSTANCE);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(RESILIENCE_INSTANCE);
    }

    @Override
    public Mono<BigDecimal> getPercentage() {
        return webClient.get()
                .uri("/mock/external/percentage")
                .retrieve()
                .bodyToMono(PercentageResponse.class)
                .map(PercentageResponse::percentage)
                .doOnError(ex -> log.warn("Intento fallido al consultar el servicio externo de porcentaje: {}", ex.getMessage()))
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorResume(ex -> {
                    log.error("Servicio externo de porcentaje no disponible luego de los reintentos configurados", ex);
                    return Mono.error(new ExternalServiceUnavailableException(
                            "No fue posible obtener el porcentaje del servicio externo tras varios intentos", ex));
                });
    }
}
