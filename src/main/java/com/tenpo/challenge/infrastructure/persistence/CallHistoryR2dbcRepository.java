package com.tenpo.challenge.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/** Repositorio Spring Data R2DBC de bajo nivel; la paginación se resuelve aparte con R2dbcEntityTemplate. */
public interface CallHistoryR2dbcRepository extends ReactiveCrudRepository<CallHistoryEntity, UUID> {
}
