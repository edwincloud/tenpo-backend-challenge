package com.tenpo.challenge.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tenpo.challenge.application.CalculationService;
import com.tenpo.challenge.domain.port.PercentageProvider;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CalculationServiceTest {

    @Mock
    private PercentageProvider percentageProvider;

    @Test
    void deberiaSumarNumerosYAplicarElPorcentajeDelServicioExterno() {
        when(percentageProvider.getPercentage()).thenReturn(Mono.just(BigDecimal.TEN));
        CalculationService service = new CalculationService(percentageProvider);

        // Ejemplo del enunciado: num1=5, num2=5, 10% -> 11
        StepVerifier.create(service.calculate(BigDecimal.valueOf(5), BigDecimal.valueOf(5)))
                .assertNext(result -> {
                    assertThat(result.result()).isEqualByComparingTo("11");
                    assertThat(result.percentageApplied()).isEqualByComparingTo("10");
                })
                .verifyComplete();
    }

    @Test
    void deberiaPropagarElErrorSiElServicioExternoFalla() {
        RuntimeException fallo = new RuntimeException("servicio externo caído");
        when(percentageProvider.getPercentage()).thenReturn(Mono.error(fallo));
        CalculationService service = new CalculationService(percentageProvider);

        StepVerifier.create(service.calculate(BigDecimal.ONE, BigDecimal.ONE))
                .expectErrorMatches(ex -> ex == fallo)
                .verify();
    }

    @Test
    void deberiaFuncionarConPorcentajeCero() {
        when(percentageProvider.getPercentage()).thenReturn(Mono.just(BigDecimal.ZERO));
        CalculationService service = new CalculationService(percentageProvider);

        StepVerifier.create(service.calculate(BigDecimal.valueOf(3), BigDecimal.valueOf(4)))
                .assertNext(result -> assertThat(result.result()).isEqualByComparingTo("7"))
                .verifyComplete();
    }
}
