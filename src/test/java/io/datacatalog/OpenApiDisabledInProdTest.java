package io.datacatalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenAPI spec and Swagger UI are developer conveniences; a production deployment
 * (the {@code prod} profile) must not expose them.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class OpenApiDisabledInProdTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void apiDocsAreNotExposedUnderProdProfile() {
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void swaggerUiIsNotExposedUnderProdProfile() {
        assertThat(rest.getForEntity("/swagger-ui/index.html", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
