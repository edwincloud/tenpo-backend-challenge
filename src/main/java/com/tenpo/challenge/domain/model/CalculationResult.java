package com.tenpo.challenge.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * El resultado del cálculo: num1 + num2, más el porcentaje que trae el proveedor externo.
 */
public record CalculationResult(BigDecimal num1, BigDecimal num2, BigDecimal percentageApplied, BigDecimal result) {

    // Guardamos 6 decimales de precisión mientras se calcula, para no perder exactitud;
    // después, al mostrarlo, se redondea a 2 decimales nomás.
    private static final int SCALE = 6;

    /**
     * La fórmula del challenge: (num1 + num2) + ese porcentaje sobre la suma.
     * Ej: num1=5, num2=5, percentage=10 -> (5+5) + 10% de 10 = 11
     */
    public static CalculationResult of(BigDecimal num1, BigDecimal num2, BigDecimal percentage) {
        BigDecimal sum = num1.add(num2);
        BigDecimal increment = sum.multiply(percentage).divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
        BigDecimal result = sum.add(increment);
        return new CalculationResult(num1, num2, percentage, result);
    }
}
