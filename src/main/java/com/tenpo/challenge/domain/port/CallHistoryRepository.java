package com.tenpo.challenge.domain.port;

import com.tenpo.challenge.domain.model.CallRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para guardar/leer el historial de llamadas.
 * La escritura va aparte del hilo principal del request, no lo bloquea.
 */
public interface CallHistoryRepository {

    Mono<Void> save(CallRecord record);

    Mono<Page<CallRecord>> findAll(Pageable pageable);
}
