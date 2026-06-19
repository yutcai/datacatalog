package io.datacatalog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, S3TestcontainersConfiguration.class})
@SuppressWarnings("unchecked")
class VersionUploadTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void twoStepUploadThenDownloadRoundTrips() throws Exception {
        String token = authedUser("uploader");
        String datasetId = createDataset(token);
        byte[] content = "hello data catalog".getBytes(StandardCharsets.UTF_8);

        // 1. request upload -> PENDING version + pre-signed PUT URL
        ResponseEntity<Map> requested = post("/v1/datasets/" + datasetId + "/versions", Map.of(), token);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String versionId = (String) requested.getBody().get("versionId");
        String uploadUrl = (String) requested.getBody().get("uploadUrl");
        assertThat(versionId).isNotNull();
        assertThat(uploadUrl).startsWith("http");

        // 2. client uploads bytes directly to S3 (bytes never touch the app tier)
        assertThat(httpPut(uploadUrl, content)).isEqualTo(200);

        // 3. complete -> server verifies the object landed, flips PENDING -> ACTIVE
        ResponseEntity<Map> completed = post(
                "/v1/datasets/" + datasetId + "/versions/" + versionId + "/complete", Map.of(), token);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completed.getBody()).containsEntry("state", "ACTIVE");
        assertThat(((Number) completed.getBody().get("sizeBytes")).longValue()).isEqualTo(content.length);

        // 4. download -> pre-signed GET URL returns the same bytes
        ResponseEntity<Map> download = get(
                "/v1/datasets/" + datasetId + "/versions/" + versionId + "/download", token);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        String downloadUrl = (String) download.getBody().get("downloadUrl");
        assertThat(httpGet(downloadUrl)).isEqualTo(content);
    }

    @Test
    void downloadingAPendingVersionIsRejected() {
        String token = authedUser("pending-dl");
        String datasetId = createDataset(token);
        String versionId = (String) post("/v1/datasets/" + datasetId + "/versions", Map.of(), token)
                .getBody().get("versionId");

        // never uploaded / completed -> still PENDING
        ResponseEntity<Map> download = get(
                "/v1/datasets/" + datasetId + "/versions/" + versionId + "/download", token);

        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void completingWithoutUploadingIsRejected() {
        String token = authedUser("no-upload");
        String datasetId = createDataset(token);
        String versionId = (String) post("/v1/datasets/" + datasetId + "/versions", Map.of(), token)
                .getBody().get("versionId");

        // object was never PUT to S3 -> complete must not flip to ACTIVE
        ResponseEntity<Map> completed = post(
                "/v1/datasets/" + datasetId + "/versions/" + versionId + "/complete", Map.of(), token);

        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void requestUploadOnUnknownDatasetIs404() {
        String token = authedUser("ghost");
        ResponseEntity<Map> resp = post("/v1/datasets/" + UUID.randomUUID() + "/versions", Map.of(), token);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void requestUploadWithoutTokenIsUnauthorized() {
        ResponseEntity<Map> resp = post("/v1/datasets/" + UUID.randomUUID() + "/versions", Map.of(), null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- helpers ---

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

    private ResponseEntity<Map> get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private int httpPut(String url, byte[] body) throws Exception {
        HttpResponse<Void> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.discarding());
        return resp.statusCode();
    }

    private byte[] httpGet(String url) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray()).body();
    }
}
