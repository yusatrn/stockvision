package com.stockvision.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 specification metadata and security scheme definitions for the StockVision AI REST API.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI stockVisionOpenApi() {
        return new OpenAPI()
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description("Stateless JWT access token")))
                .info(
                        new Info()
                                .title("StockVision AI API")
                                .version("v1")
                                .description(
                                        "Borsa Istanbul-first stock market analysis and decision-support API. "
                                                + "Does not provide investment advice, guaranteed returns, or brokerage order execution."));
    }
}
