package io.datacatalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PATCH /v1/datasets/{id} — partial update, owner-only, metadata merged by key.
 *
 * PATCH is sent via java.net.http.HttpClient because TestRestTemplate's default JDK
 * connection factory cannot issue the PATCH verb.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class DatasetPatchTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void ownerCanUpdateScalarFieldsAndTags() throws Exception {
        String token = authedUser("editor");
        String id = create(token, Map.of("name", "before", "team", "old", "tags", List.of("a")));

        Response resp = patch(id, Map.of(
                "name", "after", "team", "new", "description", "now described",
                "tags", List.of("x", "y")), token);

        assertThat(resp.status).isEqualTo(200);
        assertThat(resp.body).containsEntry("name", "after").containsEntry("team", "new")
                .containsEntry("description", "now described");
        assertThat((List<String>) resp.body.get("tags")).containsExactlyInAnyOrder("x", "y");
    }

    @Test
    void metadataIsMergedByKeyNotReplaced() throws Exception {
        String token = authedUser("merger");
        String id = create(token, Map.of("name", "m",
                "metadata", Map.of("a", 1, "b", 2)));

        Response resp = patch(id, Map.of("metadata", Map.of("b", 3, "c", 4)), token);

        assertThat(resp.status).isEqualTo(200);
        // a preserved, b overwritten, c added.
        assertThat((Map<String, Object>) resp.body.get("metadata"))
                .containsEntry("a", 1).containsEntry("b", 3).containsEntry("c", 4);
    }

    @Test
    void omittedFieldsAreLeftUnchanged() throws Exception {
        String token = authedUser("partial");
        String id = create(token, Map.of("name", "keep-me", "description", "keep-this",
                "tags", List.of("keep")));

        Response resp = patch(id, Map.of("metadata", Map.of("k", "v")), token);

        assertThat(resp.body).containsEntry("name", "keep-me").containsEntry("description", "keep-this");
        assertThat((List<String>) resp.body.get("tags")).containsExactly("keep");
        assertThat((Map<String, Object>) resp.body.get("metadata")).containsEntry("k", "v");
    }

    @Test
    void updatedAtAdvancesOnPatch() throws Exception {
        String token = authedUser("clock");
        String id = create(token, Map.of("name", "tick"));

        Response resp = patch(id, Map.of("team", "tock"), token);

        OffsetDateTime created = OffsetDateTime.parse((String) resp.body.get("createdAt"));
        OffsetDateTime updated = OffsetDateTime.parse((String) resp.body.get("updatedAt"));
        assertThat(updated).isAfter(created);
    }

    @Test
    void nonOwnerCannotPatch() throws Exception {
        String ownerToken = authedUser("real-owner");
        String id = create(ownerToken, Map.of("name", "guarded"));
        String intruderToken = authedUser("intruder");

        Response resp = patch(id, Map.of("name", "hijacked"), intruderToken);

        assertThat(resp.status).isEqualTo(403);
    }

    @Test
    void unknownDatasetIs404() throws Exception {
        String token = authedUser("seeker");
        Response resp = patch(UUID.randomUUID().toString(), Map.of("name", "x"), token);
        assertThat(resp.status).isEqualTo(404);
    }

    @Test
    void patchWithoutTokenIsUnauthorized() throws Exception {
        String token = authedUser("owner");
        String id = create(token, Map.of("name", "locked"));

        Response resp = patch(id, Map.of("name", "x"), null);

        assertThat(resp.status).isEqualTo(401);
    }

    // --- helpers ---

    private String tokenUsername;

    private record Response(int status, Map<String, Object> body) {
    }

    private Response patch(String id, Map<String, Object> body, String token) throws Exception {
        // rest.getRootUri() is the embedded test server's own base (http://localhost:<random-port>),
        // resolved by Spring — not an environment endpoint, so nothing to configure here.
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(rest.getRootUri() + "/v1/datasets/" + id))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req.build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> parsed = (resp.body() == null || resp.body().isBlank())
                ? Map.of() : mapper.readValue(resp.body(), Map.class);
        return new Response(resp.statusCode(), parsed);
    }

    private String create(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        ResponseEntity<Map> resp = rest.postForEntity("/v1/datasets",
                new HttpEntity<>(body, headers), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) resp.getBody().get("id");
    }

    private String authedUser(String prefix) {
        tokenUsername = prefix + "-" + UUID.randomUUID();
        rest.postForEntity("/v1/auth/register",
                Map.of("username", tokenUsername, "password", "pw-12345"), Map.class);
        ResponseEntity<Map> token = rest.postForEntity("/v1/auth/token",
                Map.of("username", tokenUsername, "password", "pw-12345"), Map.class);
        return (String) token.getBody().get("accessToken");
    }
}
