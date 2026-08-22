package com.tenpo.challenge.application;

import com.tenpo.challenge.domain.model.CallRecord;
import com.tenpo.challenge.domain.port.CallHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Guarda el historial de llamadas sin bloquear al que la llama (no puede afectar el
 * tiempo de respuesta del endpoint principal), y permite consultarlo paginado.
 *
 * El guardado se dispara con un subscribe() propio en un scheduler aparte (con virtual
 * threads, ver VirtualThreadSchedulerConfig): quien llama a recordAsync(...) sigue de
 * largo, no espera a que termine de escribirse en la base.
 */
@Service
public class CallHistoryService {

    private static final Logger log = LoggerFactory.getLogger(CallHistoryService.class);

    private final CallHistoryRepository repository;
    private final Scheduler historyWriteScheduler;

    public CallHistoryService(CallHistoryRepository repository, Scheduler historyWriteScheduler) {
        this.repository = repository;
        this.historyWriteScheduler = historyWriteScheduler;
    }

    public void recordAsync(CallRecord record) {
        repository.save(record)
                .subscribeOn(historyWriteScheduler)
                .subscribe(
                        v -> { },
                        ex -> log.error("No se pudo persistir el historial de la llamada a {}", record.endpoint(), ex));
    }

    public Mono<Page<CallRecord>> getHistory(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
