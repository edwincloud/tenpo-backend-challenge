package com.tenpo.challenge.integration;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prueba el límite de 3 RPM (requisito 4).
 *
 * Uso DEFINED_PORT con un puerto fijo, distinto al de los demás tests de integración, por
 * dos motivos: (1) igual que en CalculationHistoryIntegrationTest, "external.percentage-
 * service.base-url" necesita resolver ${server.port} al armar el WebClient, cosa que no
 * funciona con puerto aleatorio; (2) un puerto fijo distinto por clase asegura que Spring
 * levante un ApplicationContext propio para este test (con su propio RateLimiter en
 * memoria), en vez de reusar por caché de contexto el mismo que usan los otros tests -eso
 * arruinaría el conteo de 3 RPM mezclándolo con requests de otras clases-.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = "server.port=18082")
@Testcontainers
class RateLimitIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("tenpo_challenge_it")
            .withUsername("tenpo")
            .withPassword("tenpo");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void laCuartaLlamadaEnUnMinuto_devuelve429() {
        for (int i = 0; i < 3; i++) {
            webTestClient.post().uri("/api/v1/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("num1", 1, "num2", 1))
                    .exchange()
                    .expectStatus().isOk();
        }

        webTestClient.post().uri("/api/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("num1", 1, "num2", 1))
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectBody()
                .jsonPath("$.message").exists();
    }
}
