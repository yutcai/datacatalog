package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Pins the "ships dark" property of the embeddings backfill: under the default
 * configuration only {@code health} is exposed, so the backfill endpoint is unreachable —
 * even with valid credentials — until an operator opts in via
 * {@code management.endpoints.web.exposure.include}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class EmbeddingBackfillNotExposedByDefaultTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void backfillIsNotReachableUnlessExplicitlyExposed() {
        String token = authedUser();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<Map> response =
                rest.exchange("/embeddings", HttpMethod.POST, new HttpEntity<>(null, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String authedUser() {
        String username = "dark-caller-" + UUID.randomUUID();
        rest.postForEntity("/v1/auth/register", Map.of("username", username, "password", "pw-12345"), Map.class);
        ResponseEntity<Map> token =
                rest.postForEntity("/v1/auth/token", Map.of("username", username, "password", "pw-12345"), Map.class);
        return (String) token.getBody().get("accessToken");
    }
}
