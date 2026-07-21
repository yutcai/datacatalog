package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void apiDocsArePublicAndDescribeTheApi() {
        // No bearer token: the spec endpoint must be reachable for tooling.
        ResponseEntity<String> resp = rest.getForEntity("/v3/api-docs", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("/v1/auth/token").contains("/v1/me").contains("bearer-jwt");
    }

    @Test
    void swaggerUiIsPubliclyAccessible() {
        ResponseEntity<String> resp = rest.getForEntity("/swagger-ui/index.html", String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
