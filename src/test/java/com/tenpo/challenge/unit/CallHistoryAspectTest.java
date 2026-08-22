package com.tenpo.challenge.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tenpo.challenge.application.CallHistoryService;
import com.tenpo.challenge.domain.model.CallRecord;
import com.tenpo.challenge.infrastructure.web.CallHistoryAspect;
import com.tenpo.challenge.infrastructure.web.RecordHistory;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CallHistoryAspectTest {

    @Mock
    private CallHistoryService callHistoryService;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private CallHistoryAspect aspect;
    private RecordHistory recordHistory;

    @BeforeEach
    void setUp() {
        aspect = new CallHistoryAspect(callHistoryService, new ObjectMapper());
        recordHistory = mock(RecordHistory.class);
        when(recordHistory.endpoint()).thenReturn("/api/v1/calculate");
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @Test
    void llamadaExitosa_quedaRegistradaConElStatusYCuerpoDeLaRespuesta() throws Throwable {
        when(signature.getParameterNames()).thenReturn(new String[]{"request"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{new SampleRequest("5", "5")});
        when(joinPoint.proceed()).thenReturn(Mono.just(ResponseEntity.ok(new SampleResponse("11"))));

        Object result = aspect.aroundRecordedEndpoint(joinPoint, recordHistory);

        StepVerifier.create((Mono<?>) result)
                .expectNextCount(1)
                .verifyComplete();

        verify(callHistoryService, timeout(1000)).recordAsync(argThat(record ->
                record.endpoint().equals("/api/v1/calculate")
                        && record.statusCode() == 200
                        && record.requestParams().contains("\"request\"")
                        && record.responseBody().contains("11")));
    }

    @Test
    void llamadaConError_quedaRegistradaConElStatusDelError() throws Throwable {
        when(signature.getParameterNames()).thenReturn(new String[]{"request"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{new SampleRequest("5", "5")});
        when(joinPoint.proceed()).thenReturn(Mono.error(new ServerWebInputException("num1 inválido")));

        Object result = aspect.aroundRecordedEndpoint(joinPoint, recordHistory);

        StepVerifier.create((Mono<?>) result)
                .expectError(ServerWebInputException.class)
                .verify();

        verify(callHistoryService, timeout(1000)).recordAsync(argThat(record ->
                record.statusCode() == HttpStatus.BAD_REQUEST.value()
                        && record.errorMessage() != null));
    }

    @Test
    void siElResultadoNoEsUnMono_loDejaPasarSinRegistrar() throws Throwable {
        when(joinPoint.proceed()).thenReturn("no-reactivo");

        Object result = aspect.aroundRecordedEndpoint(joinPoint, recordHistory);

        assertThat(result).isEqualTo("no-reactivo");
    }

    private record SampleRequest(String num1, String num2) {
    }

    private record SampleResponse(String result) {
    }
}
