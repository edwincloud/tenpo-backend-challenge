package com.tenpo.challenge.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenpo.challenge.domain.exception.ExternalServiceUnavailableException;
import com.tenpo.challenge.infrastructure.web.handler.HttpStatusResolver;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

class HttpStatusResolverTest {

    @Test
    void responseStatusException_devuelveSuPropioStatus() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "conflicto");
        assertThat(HttpStatusResolver.resolve(ex)).isEqualTo(409);
    }

    @Test
    void serverWebInputException_es400() {
        assertThat(HttpStatusResolver.resolve(new ServerWebInputException("bad input"))).isEqualTo(400);
    }

    @Test
    void requestNotPermitted_es429() {
        RateLimiter rateLimiter = RateLimiter.ofDefaults("test");
        assertThat(HttpStatusResolver.resolve(RequestNotPermitted.createRequestNotPermitted(rateLimiter))).isEqualTo(429);
    }

    @Test
    void callNotPermitted_es503() {
        CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("test");
        assertThat(HttpStatusResolver.resolve(CallNotPermittedException.createCallNotPermittedException(circuitBreaker))).isEqualTo(503);
    }

    @Test
    void externalServiceUnavailable_es503() {
        ExternalServiceUnavailableException ex = new ExternalServiceUnavailableException("caído", new RuntimeException());
        assertThat(HttpStatusResolver.resolve(ex)).isEqualTo(503);
    }

    @Test
    void cualquierOtraExcepcion_es500() {
        assertThat(HttpStatusResolver.resolve(new IllegalStateException("boom"))).isEqualTo(500);
    }

    @Test
    void resolveMessage_paraResponseStatusException_devuelveElReasonLimpio() {
        // getMessage() de ServerWebInputException incluiría "400 BAD_REQUEST \"...\"";
        // resolveMessage() debe devolver solo el texto puro.
        ServerWebInputException ex = new ServerWebInputException("num1 es obligatorio");

        assertThat(HttpStatusResolver.resolveMessage(ex)).isEqualTo("num1 es obligatorio");
    }

    @Test
    void resolveMessage_paraExcepcionGenerica_devuelveGetMessage() {
        assertThat(HttpStatusResolver.resolveMessage(new IllegalStateException("boom"))).isEqualTo("boom");
    }
}
