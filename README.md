# Tenpo Backend Challenge

API REST reactiva (Spring Boot 4 + Spring WebFlux + Java 21) que resuelve el challenge
técnico de Tenpo: cálculo con porcentaje dinámico obtenido de un servicio externo,
reintentos y circuit breaker ante fallos, historial de llamadas asíncrono y paginado, rate
limiting y manejo centralizado de errores.

**Stack**: Java 21 · Spring Boot 4.1.1 · Spring WebFlux · R2DBC · PostgreSQL 16 · Flyway ·
Resilience4j · Gradle 9.6.1 (wrapper incluido) · Testcontainers · Docker

## Índice

- [Cumplimiento del challenge](#cumplimiento-del-challenge)
- [Cómo levantar el proyecto](#cómo-levantar-el-proyecto)
- [Cómo probar la API](#cómo-probar-la-api)
- [Arquitectura](#arquitectura)
- [Decisiones técnicas sobre lo solicitado](#decisiones-técnicas-sobre-lo-solicitado)
- [Extras agregados sobre el alcance del challenge](#extras-agregados-sobre-el-alcance-del-challenge)
- [Testing](#testing)
- [Estructura del proyecto](#estructura-del-proyecto)

## Cumplimiento del challenge

Mapeo directo contra los puntos pedidos en el enunciado.

### Funcionalidades principales

| # | Requisito | Dónde está |
|---|-----------|------------|
| 1 | Cálculo con porcentaje dinámico (`num1 + num2` + % de un servicio externo) | `POST /api/v1/calculate` → `CalculationService` + `ExternalPercentageClient` |
| 2 | Reintentos ante fallos del servicio externo (máx. 3 intentos) | `ExternalPercentageClient` (Retry + Circuit Breaker de Resilience4j) |
| 3 | Historial de llamadas (fecha, endpoint, parámetros, respuesta/error), asíncrono y paginado | `GET /api/v1/history?page=&size=` + `CallHistoryAspect` (registro fire-and-forget) |
| 4 | Rate limiting: máximo 3 RPM, con `429` y mensaje descriptivo | `RateLimitingWebFilter` |
| 5 | Manejo de errores HTTP 4XX/5XX con mensajes descriptivos | `GlobalExceptionHandler` (`ProblemDetail` / RFC 7807) |

### Requerimientos técnicos

| # | Requisito | Dónde está |
|---|-----------|------------|
| 1 | PostgreSQL en Docker, vía Docker Compose | `docker-compose.yml` (servicio `postgres`) |
| 2 | API en contenedor Docker + imagen publicada + `docker-compose.yml` | `Dockerfile` (build multi-stage) + `docker-compose.yml` |
| 3 | Documentación (Swagger o Postman) + instrucciones de despliegue y prueba | Swagger UI (`/swagger-ui.html`) + [`postman_collection.json`](./postman_collection.json) + este README |
| 4 | Bonus: WebFlux/reactivo, tests unitarios, análisis técnico en el README | Ver [Extras](#extras-agregados-sobre-el-alcance-del-challenge) y [Decisiones técnicas](#decisiones-técnicas-sobre-lo-solicitado) |

## Cómo levantar el proyecto

### Requisitos

- Docker y Docker Compose
- (Opcional, solo para desarrollo local sin contenedor) JDK 21 — el proyecto usa Gradle con
  su propio wrapper (`./gradlew`), así que no hace falta tener Gradle instalado aparte.

### Opción 1: todo con Docker Compose (recomendado)

```bash
docker compose up --build
```

Esto levanta:
- `postgres` (Postgres 16) en el puerto `5432`, con las migraciones de Flyway aplicándose
  automáticamente al arrancar la API. Si ya hay otro Postgres corriendo localmente en el
  5432, hay que cambiar el mapeo de puertos en `docker-compose.yml` (ej. `"5442:5432"`)
  antes de levantar el stack.
- `api` en el puerto `8080`.

Una vez arriba: http://localhost:8080/swagger-ui.html

### Opción 2: imagen ya publicada (sin compilar)

La imagen se publica automáticamente por CI/CD (ver [CI/CD](#cicd-github-actions)) en:

- Docker Hub: https://hub.docker.com/r/edwin290683/tenpo-backend-challenge
- GHCR: https://github.com/edwincloud/tenpo-backend-challenge/pkgs/container/tenpo-backend-challenge

Para usarla en vez de compilar localmente, edita `docker-compose.yml` y reemplaza el bloque
`build: .` del servicio `api` por:

```yaml
image: edwin290683/tenpo-backend-challenge:latest
```

y luego `docker compose up`.

### Opción 3: desarrollo local (API fuera de Docker)

```bash
docker compose up postgres -d
JAVA_HOME=<ruta a tu JDK 21> ./gradlew bootRun
```

## Cómo probar la API

### Swagger UI

http://localhost:8080/swagger-ui.html — documentación interactiva de todos los endpoints.

### Colección de Postman

Importa [`postman_collection.json`](./postman_collection.json) en Postman. Incluye:
- Cálculo con caso feliz y casos de error (validación).
- Consulta de historial paginado.
- Ejemplos para forzar fallos del servicio externo y ver el rate limit en acción.

### Ejemplos con curl

```bash
# Calcular (5 + 5) + 10% = 11
curl -X POST http://localhost:8080/api/v1/calculate \
  -H "Content-Type: application/json" \
  -d '{"num1": 5, "num2": 5}'

# Consultar historial paginado
curl "http://localhost:8080/api/v1/history?page=0&size=10"
```

## Arquitectura

Arquitectura hexagonal (puertos y adaptadores), pensada para que las reglas de negocio no
dependan de frameworks ni de infraestructura:

```
domain/            <- Modelos y puertos (interfaces). No conoce Spring, HTTP ni SQL.
  model/             CalculationResult, CallRecord
  port/              PercentageProvider, CallHistoryRepository
  exception/         ExternalServiceUnavailableException

application/       <- Casos de uso. Orquestan el dominio a través de los puertos.
  CalculationService
  CallHistoryService

infrastructure/     <- Adaptadores: HTTP entrante, HTTP saliente, persistencia, cross-cutting.
  web/               Controllers, DTOs, aspecto de historial, manejo de errores
  external/          Cliente HTTP al servicio externo + su mock
  persistence/        R2DBC (entity, repos)
  ratelimit/          Filtro de rate limiting

config/            <- Beans de configuración (WebClient, OpenAPI, virtual threads)
```

El endpoint `POST /api/v1/calculate` solo depende de `CalculationService`, que a su vez solo
conoce la interfaz `PercentageProvider` — no sabe si el porcentaje viene de HTTP, de un mock,
o de otra fuente. Eso permite testear la lógica de negocio con Mockito sin tocar HTTP ni base
de datos, y cambiar el adaptador externo sin tocar el dominio.

### Principios aplicados, con ejemplos concretos

No es una lista teórica: cada punto referencia una clase real del proyecto.

| Principio | Dónde se ve | Por qué |
|---|---|---|
| **Single Responsibility** | `CalculationService` solo suma y aplica el porcentaje; `CallHistoryService` solo persiste y consulta historial; `CalculationController` solo traduce HTTP ↔ dominio | Cada clase cambia por una única razón de negocio |
| **Open/Closed** | `PercentageProvider` es una interfaz; `ExternalPercentageClient` es una implementación reemplazable | Se puede agregar otro proveedor de porcentaje sin tocar `CalculationService` |
| **Liskov / Dependency Inversion** | `application/` depende de `domain/port/*` (interfaces), nunca de clases concretas de `infrastructure/` | El dominio no importa Spring, WebClient ni R2DBC — se puede testear con un doble de prueba |
| **Interface Segregation** | `PercentageProvider` expone un único método (`getPercentage()`); `CallHistoryRepository` expone solo `save`/`findAll` | Ningún consumidor depende de métodos que no usa |
| **DRY / punto único de verdad** | `HttpStatusResolver` traduce excepción → código HTTP una sola vez, reutilizado por `GlobalExceptionHandler` y `CallHistoryAspect` | Evita que el status code del historial y el de la respuesta real diverjan |
| **Cross-cutting concerns vía AOP, no boilerplate** | `CallHistoryAspect` + `@RecordHistory` | El registro de historial no se repite a mano en cada controller |

## Decisiones técnicas sobre lo solicitado

### Spring WebFlux (bonus del challenge)

Se optó por WebFlux (no bloqueante de punta a punta: WebClient, R2DBC, filtros reactivos)
porque el challenge lo pide como punto extra, y porque el caso de uso principal —llamar a un
servicio externo con reintentos y aplicar rate limiting— se beneficia genuinamente de un
modelo no bloqueante: mientras se espera al servicio externo (con sus reintentos) no se
retiene un hilo de la plataforma por request.

### Circuit Breaker + Retry (Resilience4j)

Implementados con los **operadores reactivos** de Resilience4j (`RetryOperator`,
`CircuitBreakerOperator`) en `ExternalPercentageClient`, en vez de las anotaciones
`@Retry`/`@CircuitBreaker`. Motivo: el orden en que Resilience4j compone esas anotaciones vía
AOP no es trivial de predecir ni de testear de forma determinista. Componiendo los operadores
de forma explícita queda claro y es la semántica correcta:

1. **Retry** (más interno): reintenta la llamada HTTP cruda hasta 3 veces.
2. **Circuit Breaker** (más externo): contabiliza el resultado *final* de cada llamada
   (ya con sus reintentos aplicados) como un único éxito o fallo. Así, si el servicio externo
   viene fallando de forma sostenida, el circuito se abre y deja de intentar (fail-fast) en
   vez de seguir reintentando contra un servicio caído.

Cualquier error que sobreviva a ambos se normaliza a `ExternalServiceUnavailableException`
(503) — el resto de la aplicación no conoce excepciones de WebClient ni de Resilience4j.

### Historial asíncrono y paginado

El registro de llamadas se implementa con un aspecto AOP (`CallHistoryAspect`, disparado por
la anotación `@RecordHistory`) que, tras obtener la respuesta (éxito o error), dispara la
escritura en Postgres en un scheduler aparte (`recordAsync`, fire-and-forget): el cliente HTTP
no espera a que termine de persistirse. La consulta (`GET /api/v1/history`) soporta
`page`/`size` sobre `R2dbcEntityTemplate`, ordenada por fecha descendente.

### Rate limiting: 3 RPM sobre `/calculate`, no por cliente

El enunciado pide "la API debe soportar un máximo de 3 RPM", sin mencionar límites por
usuario/IP. Se implementó como un único `RateLimiter` (Resilience4j) aplicado en un
`WebFilter`, con dos decisiones de alcance:

- **No por cliente**: un límite único y compartido, no por IP/API-key. Es la lectura más
  simple y directa del requisito; un límite por cliente sería una extensión razonable pero
  no estaba pedida.
- **Solo sobre `POST /api/v1/calculate`, no sobre toda `/api/**`**: el límite protege al
  endpoint que dispara una llamada (con reintentos) a un servicio externo, que es lo que
  tiene sentido proteger de abuso. Aplicarlo también a `GET /api/v1/history` no tiene el
  mismo fundamento -es una simple lectura a Postgres- y es contraproducente: le pondría un
  techo artificial a poder consultar tu propio historial de llamadas, en tensión directa con
  el requisito 3. Este ajuste de alcance salió de escribir el test de integración del
  historial: al esperar (con Awaitility) a que el registro asíncrono apareciera, cada
  reintento de consulta contaba contra el mismo límite compartido con `/calculate` y el
  propio test se autobloqueaba con 429 — señal de que el límite estaba mal alcanzado, no de
  que el test estuviera mal escrito.

### Manejo de errores 4XX/5XX

`GlobalExceptionHandler` centraliza el mapeo de excepciones a respuestas HTTP consistentes
usando `ProblemDetail` (RFC 7807): validación de entrada → 400, servicio externo agotado o
circuito abierto → 503, rate limit excedido → 429, cualquier otro error no controlado → 500
sin exponer detalles internos (stacktraces, mensajes de infraestructura) al cliente.

## Extras agregados sobre el alcance del challenge

Lo siguiente no estaba pedido en el enunciado; se agregó por iniciativa propia para un
desarrollo más prolijo, controlado y fácil de mantener.

### Arquitectura hexagonal (puertos y adaptadores)

El challenge no pide una arquitectura en particular. Se organizó el código en capas
`domain` / `application` / `infrastructure` (ver [Arquitectura](#arquitectura)) para que la
lógica de negocio sea testeable de forma aislada y para que cambiar de proveedor externo, de
motor de base de datos, etc. no obligue a tocar el dominio.

### Testcontainers para pruebas de integración

No pedido explícitamente (el bonus solo menciona "test unitarios"), pero se agregó porque los
tests unitarios con mocks no alcanzan para validar cosas como: si la paginación R2DBC arma
bien la query, si el retry/circuit breaker se comportan como se espera contra HTTP real, o si
el rate limiter responde 429 en la práctica. Ver [Testing](#testing).

### Virtual threads (Java 21)

WebFlux ya es no bloqueante de punta a punta (R2DBC, WebClient), así que los virtual threads
no resuelven ningún problema de rendimiento real aquí — no hay hilos de plataforma bloqueados
que liberar. Se incluyó igual un uso puntual y acotado: el scheduler que ejecuta la escritura
asíncrona del historial (`VirtualThreadSchedulerConfig`) está respaldado por
`Executors.newVirtualThreadPerTaskExecutor()`. Es una demostración de conocimiento de la
característica más que una necesidad arquitectónica; queda documentado así en el propio
código para que sea explícito.

### Cobertura de tests, JaCoCo y Sonar

Se configuró JaCoCo con un umbral de cobertura del **85% de líneas** como gate de build
(`./gradlew check`), excluyendo DTOs, records de dominio, clases de configuración y la clase
`main` — código sin lógica propia (getters/constructores generados) cuya cobertura no dice
nada sobre corrección real. Se evaluó apuntar a un umbral más agresivo (99%), pero un gate tan
estricto sobre código completo —incluyendo clases triviales— es frágil y no aporta señal
real; se prefirió un umbral alto pero honesto sobre el código con lógica de negocio. El
reporte HTML queda en `build/reports/jacoco/test/html/index.html` tras correr los tests.

El plugin `sonarqube` ya está configurado (apuntando al reporte XML de JaCoCo) para poder
correr `./gradlew sonar` contra un servidor SonarQube/SonarCloud propio; no se ejecutó un
análisis real porque este entorno no tiene acceso a una instancia de Sonar.

### CI/CD (GitHub Actions)

- **`.github/workflows/ci.yml`**: en cada push/PR a `main`, corre `./gradlew check`
  (build + unitarios + integración con Testcontainers + gate de cobertura) y publica los
  reportes de tests y JaCoCo como artefactos. Los runners de GitHub Actions traen Docker
  nativo en Linux, así que Testcontainers corre sin los ajustes puntuales que hacen falta en
  Docker Desktop para macOS (ver nota en la sección [Testing](#testing)).
- **`.github/workflows/docker-publish.yml`**: al mergear a `main` (una vez que CI pasa) o al
  crear un tag `v*`, construye la imagen y la publica en **GHCR** (`ghcr.io`, usa el
  `GITHUB_TOKEN` que ya provee Actions, sin configuración extra) y, si están configurados los
  secrets `DOCKERHUB_USERNAME` y `DOCKERHUB_TOKEN` del repositorio, también en **Docker Hub**.
  Si esos secrets no están configurados, el push a Docker Hub simplemente se salta sin romper
  el resto del workflow.

## Testing

```bash
./gradlew test                      # unitarios + integración
./gradlew jacocoTestReport          # reporte de cobertura en build/reports/jacoco
```

> Nota (macOS + Docker Desktop): si Testcontainers falla al detectar el entorno Docker, exporta
> `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock` antes de correr los tests.

- **Unitarios** (`src/test/java/.../unit`): Mockito puro para `CalculationService`,
  `CallHistoryService`, `CallHistoryAspect`, `GlobalExceptionHandler`, `HttpStatusResolver` y
  `RateLimitingWebFilter`; más tests de "slice" web con `WebTestClient.bindToController` para
  los controllers, sin levantar el contexto completo de Spring.
- **Integración** (`src/test/java/.../integration`), con **Testcontainers**:
  - `CalculationHistoryIntegrationTest`: flujo completo contra un Postgres real — calcular,
    verificar que quede en el historial (esperando con Awaitility, porque el registro es
    asíncrono) y validar paginación.
  - `ResilienceIntegrationTest`: reemplaza el servicio externo por un `MockWebServer`
    scripteado para devolver fallos exactos, y verifica que el retry recupere tras fallos
    transitorios, y que agotar los 3 intentos devuelva 503 con mensaje descriptivo.
  - `RateLimitIntegrationTest`: valida que la 4ª llamada en la ventana de un minuto reciba 429.

## Estructura del proyecto

```
├── build.gradle.kts
├── docker-compose.yml
├── Dockerfile
├── postman_collection.json
├── src/main/java/com/tenpo/challenge/
│   ├── domain/            (modelo, puertos, excepciones)
│   ├── application/       (casos de uso)
│   ├── infrastructure/    (web, external, persistence, ratelimit)
│   └── config/
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/      (Flyway)
└── src/test/java/com/tenpo/challenge/
    ├── unit/
    └── integration/
```
