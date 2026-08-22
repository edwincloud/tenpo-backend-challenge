package com.tenpo.challenge.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Una llamada a la API, guardada para el historial.
 */
public record CallRecord(
        UUID id,
        Instant timestamp,
        String endpoint,
        String requestParams,
        String responseBody,
        Integer statusCode,
        String errorMessage
) {

    public static CallRecord success(String endpoint, String requestParams, String responseBody, int statusCode) {
        return new CallRecord(UUID.randomUUID(), Instant.now(), endpoint, requestParams, responseBody, statusCode, null);
    }

    public static CallRecord failure(String endpoint, String requestParams, String errorMessage, int statusCode) {
        return new CallRecord(UUID.randomUUID(), Instant.now(), endpoint, requestParams, null, statusCode, errorMessage);
    }
}
