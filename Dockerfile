# Etapa de build: compila con Gradle y el JDK, sin llevar Gradle a la imagen final.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts .
RUN chmod +x gradlew
# Descarga el wrapper de Gradle y resuelve las dependencias de compilación en su propia capa:
# si build.gradle.kts no cambia, Docker reutiliza esta capa y no vuelve a bajar todo de internet.
RUN ./gradlew --no-daemon -q compileJava

COPY src src
RUN ./gradlew --no-daemon -q bootJar -x test

# Etapa final: solo el JRE + el jar, imagen liviana.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /workspace/build/libs/challenge.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
