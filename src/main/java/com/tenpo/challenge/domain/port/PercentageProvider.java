package com.tenpo.challenge.domain.port;

import java.math.BigDecimal;
import reactor.core.publisher.Mono;

/**
 * Puerto de salida para pedir el porcentaje a un servicio externo.
 * Al dominio no le importa si eso viaja por HTTP, gRPC, o es un mock.
 */
public interface PercentageProvider {

    Mono<BigDecimal> getPercentage();
}
