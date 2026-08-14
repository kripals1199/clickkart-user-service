// src/main/java/com/clickkart/user/config/OpenApiConfig.java
package com.clickkart.user.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Declares the bearer scheme so the aggregated Swagger UI at the Gateway offers an
     * Authorize box - unlike the other services documented there, every endpoint here needs a
     * real access token, so a spec without it would render a UI that can only produce 401s.
     */
    @Bean
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ClickKart User Service")
                        .version("1.0.0")
                        .description("Customer profile and shipping address book. Identity and credentials remain "
                                + "owned by the Auth Service; these endpoints act only on the profile belonging to "
                                + "the access token's own subject."))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
