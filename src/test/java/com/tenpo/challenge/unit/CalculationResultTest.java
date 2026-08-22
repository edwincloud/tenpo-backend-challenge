package com.tenpo.challenge.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenpo.challenge.domain.model.CalculationResult;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CalculationResultTest {

    @Test
    void ejemploDelEnunciado_5mas5con10porciento_da11() {
        CalculationResult result = CalculationResult.of(BigDecimal.valueOf(5), BigDecimal.valueOf(5), BigDecimal.TEN);

        assertThat(result.result()).isEqualByComparingTo("11");
    }

    @Test
    void sumaSinPorcentaje_esSoloLaSuma() {
        CalculationResult result = CalculationResult.of(BigDecimal.valueOf(2), BigDecimal.valueOf(3), BigDecimal.ZERO);

        assertThat(result.result()).isEqualByComparingTo("5");
    }

    @Test
    void aceptaNumerosNegativos() {
        CalculationResult result = CalculationResult.of(BigDecimal.valueOf(-10), BigDecimal.valueOf(4), BigDecimal.TEN);

        // (-10 + 4) = -6; -6 + 10% de -6 (-0.6) = -6.6
        assertThat(result.result()).isEqualByComparingTo("-6.6");
    }

    @Test
    void aceptaPorcentajesConDecimales() {
        CalculationResult result = CalculationResult.of(BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(12.5));

        assertThat(result.result()).isEqualByComparingTo("112.5");
    }
}
