package com.tenpo.challenge.infrastructure.web;

import com.tenpo.challenge.application.CalculationService;
import com.tenpo.challenge.infrastructure.web.dto.CalculationRequest;
import com.tenpo.challenge.infrastructure.web.dto.CalculationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "Calculation", description = "Cálculo de num1 + num2 más el porcentaje del servicio externo")
public class CalculationController {

    private final CalculationService calculationService;
    private final Validator validator;

    public CalculationController(CalculationService calculationService, Validator validator) {
        this.calculationService = calculationService;
        this.validator = validator;
    }

    @Operation(summary = "Suma num1 y num2 y les aplica el porcentaje adicional obtenido de un servicio externo")
    @PostMapping("/api/v1/calculate")
    @RecordHistory(endpoint = "/api/v1/calculate")
    public Mono<ResponseEntity<CalculationResponse>> calculate(@RequestBody CalculationRequest request) {
        // Valido "a mano" (en vez de poner @Valid en el parámetro) para que un request
        // inválido también pase por el Mono que envuelve CallHistoryAspect y quede en el
        // historial. Con @Valid, WebFlux valida antes de siquiera entrar al método, y el
        // aspecto nunca se entera de esas llamadas que fallaron.
        Set<ConstraintViolation<CalculationRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return Mono.error(new ServerWebInputException(message));
        }

        return calculationService.calculate(request.num1(), request.num2())
                .map(CalculationResponse::from)
                .map(ResponseEntity::ok);
    }
}
