package com.tenpo.challenge.infrastructure.web.dto;

import com.tenpo.challenge.domain.model.CallRecord;
import java.time.Instant;
import java.util.UUID;

/** Un ítem del historial de llamadas, tal como se expone por la API. */
public record CallRecordResponse(
        UUID id,
        Instant timestamp,
        String endpoint,
        String requestParams,
        String responseBody,
        Integer statusCode,
        String errorMessage
) {

    public static CallRecordResponse from(CallRecord record) {
        return new CallRecordResponse(
                record.id(),
                record.timestamp(),
                record.endpoint(),
                record.requestParams(),
                record.responseBody(),
                record.statusCode(),
                record.errorMessage());
    }
}
