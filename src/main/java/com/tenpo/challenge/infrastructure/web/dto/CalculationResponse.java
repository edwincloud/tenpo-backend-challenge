package com.tenpo.challenge.infrastructure.web.dto;

import com.tenpo.challenge.domain.model.CalculationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Payload de salida del endpoint de cálculo. */
public record CalculationResponse(BigDecimal num1, BigDecimal num2, BigDecimal percentageApplied, BigDecimal result) {

    // Esto es solo para mostrarlo lindo: CalculationResult internamente maneja 6 decimales
    // de precisión (ver su Javadoc). Acá se deja un scale de salida fijo y predecible.
    // Uso setScale() y no stripTrailingZeros() a propósito: ese último, con Jackson, puede
    // terminar serializando en notación científica (tipo "1E+2") si el BigDecimal queda
    // con scale negativo -es un problema conocido de Jackson con BigDecimal-.
    private static final int DISPLAY_SCALE = 2;

    public static CalculationResponse from(CalculationResult result) {
        return new CalculationResponse(
                scale(result.num1()),
                scale(result.num2()),
                scale(result.percentageApplied()),
                scale(result.result()));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }
}
