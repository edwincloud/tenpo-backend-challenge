package com.tenpo.challenge.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Levanta el contexto completo de Spring más un Postgres real vía Testcontainers, y prueba
 * el flujo de punta a punta: calcular -> queda en el historial -> se puede consultar
 * paginado. El registro es asíncrono, así que uso Awaitility en vez de asumir que ya
 * quedó escrito apenas responde el endpoint principal.
 */
// DEFINED_PORT (no RANDOM_PORT): la app necesita armar su propio "external.percentage-
// service.base-url" (http://localhost:${server.port}) al construir el WebClient, y con
// puerto aleatorio (server.port=0) ese valor todavía no está disponible en ese momento del
// ciclo de vida del contexto. Con puerto fijo, ${server.port} se resuelve bien.
// AFTER_EACH_TEST_METHOD: los dos @Test de esta clase llaman a /api/v1/calculate, y como
// comparten el mismo contexto (mismo puerto fijo) también comparten el mismo bean
// RateLimiter -las llamadas de un método se suman a las del otro contra el límite de 3 RPM-.
// Reiniciar el contexto entre métodos resetea ese estado en memoria, sin tener que reiniciar
// el contenedor de Postgres (el @Container es estático, vive afuera del ciclo del contexto).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = "server.port=18081")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Testcontainers
class CalculationHistoryIntegrationTest {

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
    void calculoCompleto_yQuedaRegistradoEnElHistorialPaginado() {
        webTestClient.post().uri("/api/v1/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("num1", 5, "num2", 5))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.result").isEqualTo(11.0)
                .jsonPath("$.percentageApplied").isEqualTo(10.0);

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                webTestClient.get().uri("/api/v1/history?page=0&size=10")
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.totalElements").isEqualTo(1)
                        .jsonPath("$.content[0].endpoint").isEqualTo("/api/v1/calculate")
                        .jsonPath("$.content[0].statusCode").isEqualTo(200)
                        .jsonPath("$.content[0].requestParams").exists());
    }

    @Test
    void history_soportaPaginacion() {
        for (int i = 0; i < 3; i++) {
            webTestClient.post().uri("/api/v1/calculate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("num1", i, "num2", i))
                    .exchange()
                    .expectStatus().isOk();
        }

        // No afirmo totalElements/totalPages en un valor absoluto: esta clase reutiliza el
        // mismo Postgres (vía Testcontainers) entre los dos métodos de test, para no pagar
        // el costo de levantar un contenedor nuevo por cada uno, así que puede haber filas
        // de otro test ya insertadas. Lo que sí se mantiene estable, sin importar el orden
        // de ejecución, es que la página pedida (size=2) respeta ese tamaño y que hay al
        // menos 3 elementos en total.
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            byte[] body = webTestClient.get().uri("/api/v1/history?page=0&size=2")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(2)
                    .jsonPath("$.size").isEqualTo(2)
                    .returnResult().getResponseBody();
            assertThat(body).isNotNull();

            JsonNode json = new ObjectMapper().readTree(body);
            assertThat(json.get("totalElements").asInt()).isGreaterThanOrEqualTo(3);
            assertThat(json.get("totalPages").asInt()).isGreaterThanOrEqualTo(2);
        });
    }
}
