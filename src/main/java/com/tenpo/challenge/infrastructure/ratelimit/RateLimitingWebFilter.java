package com.tenpo.challenge.infrastructure.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Rate limiting (requisito 4): máximo 3 RPM. Lo hago como WebFilter -en vez de anotar el
 * controller- para que el límite quede centralizado en un solo lugar.
 *
 * El límite aplica solo a POST /api/v1/calculate, no a todo /api/**: es el endpoint que
 * termina golpeando (con reintentos) a un servicio externo, que es lo que de verdad hay
 * que cuidar de abusos. Meterlo también en GET /api/v1/history no tiene mucho sentido
 * -es una simple lectura a Postgres- y encima es contraproducente: le pondría un techo
 * artificial a poder consultar tu propio historial.
 *
 * Uso RateLimiter.acquirePermission() con timeout 0 (así configurado en application.yml):
 * es no bloqueante, así que no molesta al event loop de Netty/WebFlux.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitingWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingWebFilter.class);
    private static final String RATE_LIMITER_NAME = "globalApi";
    private static final String LIMITED_PATH = "/api/v1/calculate";

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public RateLimitingWebFilter(RateLimiterRegistry registry, ObjectMapper objectMapper) {
        this.rateLimiter = registry.rateLimiter(RATE_LIMITER_NAME);
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!LIMITED_PATH.equals(path)) {
            return chain.filter(exchange);
        }

        if (rateLimiter.acquirePermission()) {
            return chain.filter(exchange);
        }

        log.warn("Rate limit excedido para {}", path);
        return tooManyRequests(exchange);
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        long retryAfterSeconds = rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod().toSeconds();
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.TOO_MANY_REQUESTS.value(),
                "error", "Too Many Requests",
                "message", "Se excedió el límite de 3 solicitudes por minuto. Intenta nuevamente en unos segundos.");

        byte[] bytes = toJsonBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] toJsonBytes(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception e) {
            return "{\"error\":\"Too Many Requests\"}".getBytes(StandardCharsets.UTF_8);
        }
    }
}
