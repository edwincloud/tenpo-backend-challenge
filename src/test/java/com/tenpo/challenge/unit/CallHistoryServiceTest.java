package com.tenpo.challenge.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tenpo.challenge.application.CallHistoryService;
import com.tenpo.challenge.domain.model.CallRecord;
import com.tenpo.challenge.domain.port.CallHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CallHistoryServiceTest {

    @Mock
    private CallHistoryRepository repository;

    private CallHistoryService service;

    @BeforeEach
    void setUp() {
        // Uso un scheduler inmediato (mismo hilo) para que el test sea determinista;
        // en producción se inyecta el scheduler de virtual threads (VirtualThreadSchedulerConfig).
        service = new CallHistoryService(repository, Schedulers.immediate());
    }

    @Test
    void recordAsync_persisteElRegistroSinBloquearAlLlamante() {
        when(repository.save(any())).thenReturn(Mono.empty());
        CallRecord record = CallRecord.success("/api/v1/calculate", "{}", "{}", 200);

        service.recordAsync(record);

        verify(repository, timeout(1000)).save(record);
    }

    @Test
    void recordAsync_siFallaLaPersistenciaNoPropagaElErrorAlLlamante() {
        when(repository.save(any())).thenReturn(Mono.error(new RuntimeException("db caída")));
        CallRecord record = CallRecord.failure("/api/v1/calculate", "{}", "timeout", 503);

        // No debe lanzar excepción: el fallo de persistencia se loguea, no revienta al caller.
        service.recordAsync(record);

        verify(repository, timeout(1000)).save(record);
    }

    @Test
    void getHistory_delegaEnElRepositorioYDevuelveLaPaginaTalCual() {
        CallRecord record = CallRecord.success("/api/v1/calculate", "{}", "{}", 200);
        Page<CallRecord> page = new PageImpl<>(java.util.List.of(record));
        when(repository.findAll(any())).thenReturn(Mono.just(page));

        StepVerifier.create(service.getHistory(PageRequest.of(0, 20)))
                .assertNext(result -> assertThat(result.getContent()).containsExactly(record))
                .verifyComplete();
    }
}
