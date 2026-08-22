package com.tenpo.challenge.infrastructure.web;

import com.tenpo.challenge.application.CallHistoryService;
import com.tenpo.challenge.domain.model.CallRecord;
import com.tenpo.challenge.infrastructure.web.handler.HttpStatusResolver;
import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * Intercepta los métodos con @RecordHistory y guarda en el historial los parámetros
 * recibidos junto con la respuesta (o el error). Sin bloquear al cliente: el guardado
 * pasa en paralelo, una vez que la respuesta ya está calculada.
 */
@Aspect
@Component
public class CallHistoryAspect {

    private final CallHistoryService callHistoryService;
    private final ObjectMapper objectMapper;

    public CallHistoryAspect(CallHistoryService callHistoryService, ObjectMapper objectMapper) {
        this.callHistoryService = callHistoryService;
        this.objectMapper = objectMapper;
    }

    @Around(value = "@annotation(recordHistory)", argNames = "joinPoint,recordHistory")
    public Object aroundRecordedEndpoint(ProceedingJoinPoint joinPoint, RecordHistory recordHistory) throws Throwable {
        String endpoint = recordHistory.endpoint();
        String requestParams = serializeParams(joinPoint);

        Object result = joinPoint.proceed();
        if (!(result instanceof Mono<?> mono)) {
            // Solo cubrimos controllers reactivos (Mono<ResponseEntity<?>>), que es lo que hay en toda la API.
            return result;
        }

        return mono
                .doOnSuccess(response -> logSuccess(endpoint, requestParams, response))
                .doOnError(ex -> logFailure(endpoint, requestParams, ex));
    }

    private void logSuccess(String endpoint, String requestParams, Object response) {
        int statusCode = 200;
        Object body = response;
        if (response instanceof ResponseEntity<?> entity) {
            statusCode = entity.getStatusCode().value();
            body = entity.getBody();
        }
        String responseBody = toJson(body);
        callHistoryService.recordAsync(CallRecord.success(endpoint, requestParams, responseBody, statusCode));
    }

    private void logFailure(String endpoint, String requestParams, Throwable ex) {
        int statusCode = HttpStatusResolver.resolve(ex);
        String errorMessage = HttpStatusResolver.resolveMessage(ex);
        callHistoryService.recordAsync(CallRecord.failure(endpoint, requestParams, errorMessage, statusCode));
    }

    private String serializeParams(ProceedingJoinPoint joinPoint) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            var params = new java.util.LinkedHashMap<String, Object>();
            for (int i = 0; i < args.length; i++) {
                // ServerWebExchange/ServerHttpResponse no se pueden serializar y tampoco aportan nada al historial.
                if (args[i] instanceof ServerWebExchange || args[i] instanceof ServerHttpResponse) {
                    continue;
                }
                params.put(paramNames[i], args[i]);
            }
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return Arrays.toString(joinPoint.getArgs());
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }
}
