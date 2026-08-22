package com.tenpo.challenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI challengeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tenpo Backend Challenge API")
                        .description("Cálculo con porcentaje dinámico, historial de llamadas, rate limiting y resiliencia ante fallos del servicio externo.")
                        .version("v1")
                        .contact(new Contact().name("Backend Challenge")));
    }
}
