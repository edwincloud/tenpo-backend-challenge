package com.tenpo.challenge.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.tenpo.challenge.infrastructure.ratelimit.RateLimitingWebFilter;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RateLimitingWebFilterTest {

    @Mock
    private RateLimiterRegistry registry;
    @Mock
    private RateLimiter rateLimiter;

    private RateLimitingWebFilter filter;

    @BeforeEach
    void setUp() {
        when(registry.rateLimiter("globalApi")).thenReturn(rateLimiter);
        filter = new RateLimitingWebFilter(registry, new ObjectMapper());
    }

    @Test
    void dejaPasarLaLlamadaSiHayPermisosDisponibles() {
        when(rateLimiter.acquirePermission()).thenReturn(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/calculate"));
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void devuelve429YNoLlegaAlControllerSiSeExcedeElLimite() {
        when(rateLimiter.acquirePermission()).thenReturn(false);
        when(rateLimiter.getRateLimiterConfig()).thenReturn(
                RateLimiterConfig.custom().limitRefreshPeriod(Duration.ofSeconds(60)).build());
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/calculate"));
        WebFilterChain chain = mock(WebFilterChain.class);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        verifyNoInteractions(chain);
    }

    @Test
    void rutasFueraDeApi_noConsumenElRateLimiter() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/swagger-ui.html"));
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(rateLimiter);
    }

    @Test
    void consultarElHistorial_noConsumeElRateLimiter() {
        // GET /history es una simple lectura; solo /calculate (que golpea al servicio
        // externo) tiene que contar contra el límite de 3 RPM.
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/history"));
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(rateLimiter);
    }
}
