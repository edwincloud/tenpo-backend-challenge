package com.tenpo.challenge.infrastructure.web;

import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Sin el charset explícito en el Content-Type, algunos clientes (por ejemplo un navegador
 * mostrando la respuesta cruda de un GET, sin pasar por el fetch de Swagger) asumen Latin-1
 * en vez de UTF-8, y ahí se rompen los acentos de los mensajes en español, aunque los bytes
 * del body ya sean UTF-8 válido. Este filtro fuerza charset=UTF-8 en toda respuesta JSON
 * antes de mandarla.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonCharsetWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        response.beforeCommit(() -> {
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null
                    && contentType.getCharset() == null
                    && "application".equals(contentType.getType())
                    && contentType.getSubtype().endsWith("json")) {
                response.getHeaders().setContentType(
                        new MediaType(contentType.getType(), contentType.getSubtype(), StandardCharsets.UTF_8));
            }
            return Mono.empty();
        });
        return chain.filter(exchange);
    }
}
