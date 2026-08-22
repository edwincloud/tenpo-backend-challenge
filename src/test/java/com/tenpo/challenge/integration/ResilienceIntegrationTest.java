package com.tenpo.challenge.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
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
 * Prueba el comportamiento real de retry + circuit breaker (requisito 2), reemplazando el
 * "servicio externo" por un MockWebServer que puedo scriptear con precisión: cuántas veces
 * falla y en qué momento empieza a responder bien. Con esto cubro dos escenarios que un mock
 * fijo (como MockExternalPercentageController) no puede garantizar de forma determinista.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ResilienceIntegrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("tenpo_challenge_it")
            .withUsername("tenpo")
            .withPassword("tenpo");

    static MockWebServer mockWebServer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        registry.add("external.percentage-service.base-url", () -> "http://localhost:" + mockWebServer.getPort());
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://%s:%d/%s".formatted(postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @AfterAll
    static void shutdownMockServer() throws IOException {
        mockWebServer.shutdown();
    }

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() throws InterruptedException {
        webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        // Limpio cualquier request que haya quedado pendiente de un test anterior antes de scriptear el siguiente.
        while (mockWebServer.takeRequest(0, java.util.concurrent.TimeUnit.MILLISECONDS) != null) {
            // no-op
        }
    }

    @Test
    void reintenta_yTerminaExitosoDespuesDeDosFallosTransitorios() {
        // getRequestCount() es acumulativo durante todo el ciclo de vida del MockWebServer
        // (no se resetea entre tests), así que mido la diferencia contra este punto.
        long requestsBefore = mockWebServer.getRequestCount();

        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"percentage\": 10}")
                .addHeader("Content-Type", "application/json"));

        webTestClient.post().uri("/api/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("num1", 5, "num2", 5))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result").isEqualTo(11.0);

        assertThat(mockWebServer.getRequestCount() - requestsBefore).isEqualTo(3);
    }

    @Test
    void agotaLosTresReintentos_yDevuelve503ConMensajeDescriptivo() {
        for (int i = 0; i < 3; i++) {
            mockWebServer.enqueue(new MockResponse().setResponseCode(503));
        }

        webTestClient.post().uri("/api/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("num1", 1, "num2", 1))
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.detail").exists();
    }
}
