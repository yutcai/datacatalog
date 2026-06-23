package io.datacatalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GET /v1/datasets/{id}/versions — list a dataset's versions so the detail page can show
 * history and download any version without re-uploading. ACTIVE only (PENDING stays invisible
 * to reads), newest first.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
@SuppressWarnings("unchecked")
class VersionListTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void listsActiveVersionsNewestFirst() throws Exception {
        String token = authedUser("lister");
        String datasetId = createDataset(token);
        upload(datasetId, token, "v1-bytes".getBytes(StandardCharsets.UTF_8));
        upload(datasetId, token, "v2-bytes-longer".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<List> resp = getList("/v1/datasets/" + datasetId + "/versions", token);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items = resp.getBody();
        assertThat(items).hasSize(2);
        assertThat(((Number) items.get(0).get("versionNumber")).intValue()).isEqualTo(2);
        assertThat(((Number) items.get(1).get("versionNumber")).intValue()).isEqualTo(1);
        assertThat(items.get(0)).containsEntry("state", "ACTIVE");
        assertThat(items.get(0).get("id")).isNotNull();
    }

    @Test
    void excludesPendingVersions() throws Exception {
        String token = authedUser("pending-excl");
        String datasetId = createDataset(token);
        upload(datasetId, token, "active".getBytes(StandardCharsets.UTF_8)); // v1 ACTIVE
        // v2 requested but never completed -> stays PENDING, must not appear
        post("/v1/datasets/" + datasetId + "/versions", Map.of(), token);

        ResponseEntity<List> resp = getList("/v1/datasets/" + datasetId + "/versions", token);

        List<Map<String, Object>> items = resp.getBody();
        assertThat(items).hasSize(1);
        assertThat(((Number) items.get(0).get("versionNumber")).intValue()).isEqualTo(1);
    }

    @Test
    void unknownDatasetIs404() {
        String token = authedUser("ghost");
        // status-only: the error body is a ProblemDetail object, not a JSON array
        ResponseEntity<String> resp = rest.exchange(
                "/v1/datasets/" + UUID.randomUUID() + "/versions", HttpMethod.GET,
                new HttpEntity<>(bearer(token)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void withoutTokenIsUnauthorized() {
        ResponseEntity<String> resp = rest.exchange(
                "/v1/datasets/" + UUID.randomUUID() + "/versions", HttpMethod.GET,
                new HttpEntity<>(bearer(null)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    // --- helpers ---

    private String upload(String datasetId, String token, byte[] content) throws Exception {
        ResponseEntity<Map> requested = post("/v1/datasets/" + datasetId + "/versions", Map.of(), token);
        String versionId = (String) requested.getBody().get("versionId");
        httpPut((String) requested.getBody().get("uploadUrl"), content);
        post("/v1/datasets/" + datasetId + "/versions/" + versionId + "/complete", Map.of(), token);
        return versionId;
    }

    private String authedUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        rest.postForEntity("/v1/auth/register",
                Map.of("username", username, "password", "pw-12345"), Map.class);
        return (String) rest.postForEntity("/v1/auth/token",
                Map.of("username", username, "password", "pw-12345"), Map.class).getBody().get("accessToken");
    }

    private String createDataset(String token) {
        return (String) post("/v1/datasets", Map.of("name", "ds-" + UUID.randomUUID()), token)
                .getBody().get("id");
    }

    private ResponseEntity<Map> post(String path, Map<String, Object> body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.postForEntity(path, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<List> getList(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), List.class);
    }

    private void httpPut(String url, byte[] body) throws Exception {
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.discarding());
    }
}
