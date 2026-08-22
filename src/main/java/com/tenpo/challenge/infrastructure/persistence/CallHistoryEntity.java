package com.tenpo.challenge.infrastructure.persistence;

import com.tenpo.challenge.domain.model.CallRecord;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

/**
 * La fila de la tabla call_history. Ver la migración V1.
 *
 * Lo de Persistable con isNew() = true no es porque sí: el id (UUID) lo generamos
 * nosotros en el dominio, no la base. Si no hacemos esto, Spring Data R2DBC ve un id
 * que no es nulo y asume "esto ya existe", entonces intenta un UPDATE en vez de un
 * INSERT -y como la fila todavía no existe, el guardado no hace nada, y ni te enterás-.
 * Esta entidad se crea solo para insertar (nunca se relee para volver a guardar), así
 * que decir siempre isNew() = true acá es correcto.
 */
@Table("call_history")
public record CallHistoryEntity(
        @Id UUID id,
        Instant callTimestamp,
        String endpoint,
        String requestParams,
        String responseBody,
        Integer statusCode,
        String errorMessage
) implements Persistable<UUID> {

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    public static CallHistoryEntity fromDomain(CallRecord record) {
        return new CallHistoryEntity(
                record.id(),
                record.timestamp(),
                record.endpoint(),
                record.requestParams(),
                record.responseBody(),
                record.statusCode(),
                record.errorMessage());
    }

    public CallRecord toDomain() {
        return new CallRecord(id, callTimestamp, endpoint, requestParams, responseBody, statusCode, errorMessage);
    }
}
