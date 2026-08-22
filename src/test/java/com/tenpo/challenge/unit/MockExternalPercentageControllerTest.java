package com.tenpo.challenge.unit;

import com.tenpo.challenge.infrastructure.external.MockExternalPercentageController;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class MockExternalPercentageControllerTest {

    @Test
    void devuelveElPorcentajeFijoConfigurado() {
        WebTestClient client = WebTestClient
                .bindToController(new MockExternalPercentageController(BigDecimal.TEN, 0))
                .build();

        client.get().uri("/mock/external/percentage")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.percentage").isEqualTo(10);
    }

    @Test
    void conFailTrue_devuelve503() {
        WebTestClient client = WebTestClient
                .bindToController(new MockExternalPercentageController(BigDecimal.TEN, 0))
                .build();

        client.get().uri("/mock/external/percentage?fail=true")
                .exchange()
                .expectStatus().isEqualTo(503);
    }
}
