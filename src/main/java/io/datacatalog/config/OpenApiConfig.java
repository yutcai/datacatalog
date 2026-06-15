package io.datacatalog.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * OpenAPI document metadata and the bearer-JWT security scheme.
 *
 * <p>Declaring the scheme here gives Swagger UI an "Authorize" button to paste a token into;
 * individual protected controllers reference it with {@code @SecurityRequirement("bearer-jwt")}
 * so the spec marks exactly the endpoints that need a token (public ones like {@code /v1/auth/**}
 * stay unmarked).
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "DataCatalog API",
                version = "v1",
                description = "Metadata-driven data catalog: datasets with queryable metadata, "
                        + "S3 pre-signed upload/download, JWT-secured."))
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
