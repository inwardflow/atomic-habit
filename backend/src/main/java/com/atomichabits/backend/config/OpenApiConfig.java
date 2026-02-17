package com.atomichabits.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI atomicHabitsOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Atomic Habits API")
                        .description("API for Atomic Habits Application")
                        .version("v0.0.1"));
    }
}
