package com.tenpo.challenge.application;

import com.tenpo.challenge.domain.model.CalculationResult;
import com.tenpo.challenge.domain.port.PercentageProvider;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * El caso de uso principal: sumar num1 + num2 y aplicarle el porcentaje que devuelve
 * el servicio externo.
 *
 * A propósito este servicio no sabe nada de HTTP, ni de cómo se consigue el porcentaje
 * (el retry/circuit breaker es cosa del adaptador PercentageProvider), ni de cómo se
 * guarda el historial (eso lo maneja un filtro aparte, para no mezclar responsabilidades).
 */
@Service
public class CalculationService {

    private final PercentageProvider percentageProvider;

    public CalculationService(PercentageProvider percentageProvider) {
        this.percentageProvider = percentageProvider;
    }

    public Mono<CalculationResult> calculate(BigDecimal num1, BigDecimal num2) {
        return percentageProvider.getPercentage()
                .map(percentage -> CalculationResult.of(num1, num2, percentage));
    }
}
