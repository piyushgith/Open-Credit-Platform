package com.opencredit.platform.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata shown on the Swagger UI (served at {@code /swagger-ui.html}) and
 * the generated OpenAPI document (at {@code /v3/api-docs}).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Open Credit Platform API",
                version = "v1",
                description = "Polymorphic loan origination API: a single endpoint routes each loan product "
                        + "to its own decisioning strategy and returns every response in a standardized envelope."
        )
)
public class OpenApiConfig {
}
