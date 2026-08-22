package com.tenpo.challenge.infrastructure.external;

import java.math.BigDecimal;

/** Payload devuelto por el servicio externo (o su mock) con el porcentaje vigente. */
public record PercentageResponse(BigDecimal percentage) {
}
