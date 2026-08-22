import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
    id("org.sonarqube") version "7.4.0.8496"
}

group = "com.tenpo"
version = "1.0.0"
description = "Tenpo Backend Challenge - API REST reactiva con Spring WebFlux"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

val resilience4jVersion = "2.4.0"
val springdocVersion = "3.1.0"
val testcontainersVersion = "2.0.5"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    runtimeOnly("org.postgresql:r2dbc-postgresql")

    // Flyway corre las migraciones vía JDBC en el arranque (R2DBC no soporta migraciones).
    // En Boot 4, Flyway se modularizó: hace falta el starter (que trae la autoconfiguración
    // spring-boot-flyway + spring-boot-starter-jdbc), no alcanza con flyway-core suelto.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("io.github.resilience4j:resilience4j-spring-boot4:$resilience4jVersion")
    implementation("io.github.resilience4j:resilience4j-reactor:$resilience4jVersion")
    // Spring Boot 4 eliminó el starter "spring-boot-starter-aop" como tal; spring-aop ya
    // viene transitivamente con spring-context, así que solo falta aspectjweaver para que
    // @CircuitBreaker/@Retry y @RecordHistory funcionen como aspectos AOP sobre el bean.
    implementation("org.aspectj:aspectjweaver")

    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:$springdocVersion")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:testcontainers-r2dbc:$testcontainersVersion")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.awaitility:awaitility:4.2.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("challenge.jar")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    // Excluimos records/DTOs, config y la clase main: código sin lógica propia
    // (getters/builders generados), medirlos solo infla el número sin decir nada
    // del comportamiento real cubierto.
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/ChallengeApplication.class",
                    "**/dto/**",
                    "**/config/**",
                    "**/domain/model/**",
                    "**/domain/exception/**",
                    "**/infrastructure/persistence/CallHistoryEntity.class"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.85".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

sonarqube {
    properties {
        property("sonar.organization", "tenpo-challenge")
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.coverage.jacoco.xmlReportPaths", "${layout.buildDirectory.get()}/reports/jacoco/test/jacocoTestReport.xml")
    }
}
