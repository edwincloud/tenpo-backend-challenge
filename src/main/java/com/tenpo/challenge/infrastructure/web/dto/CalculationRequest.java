package com.tenpo.challenge.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Payload de entrada del endpoint de cálculo: dos números a sumar. */
public record CalculationRequest(

        @Schema(example = "5", description = "Primer número a sumar")
        @NotNull(message = "num1 es obligatorio")
        BigDecimal num1,

        @Schema(example = "5", description = "Segundo número a sumar")
        @NotNull(message = "num2 es obligatorio")
        BigDecimal num2
) {
}
