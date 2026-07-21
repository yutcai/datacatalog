package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class DatasetTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void createReturnsTheStoredDataset() {
        String token = authedUser("creator");
        Map<String, Object> body = Map.of(
                "name", "sales-2025",
                "team", "analytics",
                "description", "quarterly sales",
                "tags", List.of("sales", "emea"),
                "metadata", Map.of("region", "emea", "format", "parquet"));

        ResponseEntity<Map> resp = post("/v1/datasets", body, token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("id")).isNotNull();
        assertThat(resp.getBody()).containsEntry("name", "sales-2025");
        assertThat((List<String>) resp.getBody().get("tags")).containsExactlyInAnyOrder("sales", "emea");
        assertThat((Map<String, Object>) resp.getBody().get("metadata")).containsEntry("region", "emea");
    }

    @Test
    void getReturnsACreatedDataset() {
        String token = authedUser("reader");
        String id = (String) post("/v1/datasets", Map.of("name", "inventory", "metadata", Map.of("rows", 1000)), token)
                .getBody()
                .get("id");

        ResponseEntity<Map> resp = get("/v1/datasets/" + id, token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsEntry("id", id).containsEntry("name", "inventory");
        assertThat((Map<String, Object>) resp.getBody().get("metadata")).containsEntry("rows", 1000);
    }

    @Test
    void ownerIsDerivedFromTheTokenNotTheBody() {
        String token = authedUser("real-owner");
        // Body tries to claim a different owner; it must be ignored.
        Map<String, Object> body = Map.of("name", "x", "ownerUsername", "someone-else", "owner", "someone-else");

        ResponseEntity<Map> resp = post("/v1/datasets", body, token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().get("ownerUsername")).isEqualTo(tokenUsername);
    }

    @Test
    void unknownDatasetIs404() {
        String token = authedUser("seeker");
        ResponseEntity<Map> resp = get("/v1/datasets/" + UUID.randomUUID(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createWithoutTokenIsUnauthorized() {
        ResponseEntity<Map> resp = post("/v1/datasets", Map.of("name", "nope"), null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void createWithoutNameIsBadRequest() {
        String token = authedUser("validator");
        ResponseEntity<Map> resp = post("/v1/datasets", Map.of("description", "no name"), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- helpers ---

    private String tokenUsername;

    private String authedUser(String prefix) {
        tokenUsername = prefix + "-" + UUID.randomUUID();
        rest.postForEntity("/v1/auth/register", Map.of("username", tokenUsername, "password", "pw-12345"), Map.class);
        ResponseEntity<Map> token = rest.postForEntity(
                "/v1/auth/token", Map.of("username", tokenUsername, "password", "pw-12345"), Map.class);
        return (String) token.getBody().get("accessToken");
    }

    private ResponseEntity<Map> post(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.postForEntity(path, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
