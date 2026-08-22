package com.tenpo.challenge.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tenpo.challenge.infrastructure.web.dto.CalculationRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CalculationRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void requestValido_noTieneViolaciones() {
        CalculationRequest request = new CalculationRequest(BigDecimal.ONE, BigDecimal.TEN);

        Set<ConstraintViolation<CalculationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void num1Nulo_esInvalido() {
        CalculationRequest request = new CalculationRequest(null, BigDecimal.TEN);

        Set<ConstraintViolation<CalculationRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("num1");
    }

    @Test
    void num2Nulo_esInvalido() {
        CalculationRequest request = new CalculationRequest(BigDecimal.ONE, null);

        Set<ConstraintViolation<CalculationRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("num2");
    }
}
