package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.datacatalog.dataset.Dataset;
import io.datacatalog.dataset.DatasetRepository;
import io.datacatalog.embedding.FakeEmbeddingClient;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The embeddings backfill is a management operation, not a public API endpoint: it is an
 * actuator write operation (POST /embeddings) that an operator exposes explicitly — done
 * here with a test property. By default it is not exposed at all; see
 * {@link EmbeddingBackfillNotExposedByDefaultTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoints.web.exposure.include=health,embeddings")
@Import(TestcontainersConfiguration.class)
@SuppressWarnings("unchecked")
class EmbeddingBackfillTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private DatasetRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    // The backfill embeds through the same deterministic fake, so expected vectors can be
    // computed directly from the text the backfill is supposed to assemble.
    private final FakeEmbeddingClient fake = new FakeEmbeddingClient();

    @Test
    void backfillEmbedsRowsMissingAnEmbedding() {
        String token = authedUser();
        UUID ownerId = insertUser();
        // Rows saved through the repository bypass the service's write-path embedding —
        // exactly the state of rows that pre-date the embedding feature.
        UUID full = repository
                .save(new Dataset("legacy-orders", ownerId, "ops", "raw order events", List.of("orders"), null))
                .getId();
        UUID bare = repository
                .save(new Dataset("legacy-bare", ownerId, null, null, null, null))
                .getId();

        ResponseEntity<Map> first = postBackfill(token);

        // Other test classes may leave unembedded rows of their own in the shared database,
        // so the reported count is a floor, not an exact match.
        assertThat(((Number) first.getBody().get("backfilled")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(embeddingOf(full)).isEqualTo(fake.embed("legacy-orders raw order events orders"));
        assertThat(embeddingOf(bare)).isEqualTo(fake.embed("legacy-bare"));

        // The first run drained every missing embedding, so a second run has nothing to do.
        // Same method, not a separate test: this count is only deterministic right after a run.
        ResponseEntity<Map> second = postBackfill(token);
        assertThat(((Number) second.getBody().get("backfilled")).intValue()).isZero();
    }

    @Test
    void backfillLeavesAlreadyEmbeddedRowsUntouched() {
        String token = authedUser();
        UUID ownerId = insertUser();
        // A sentinel vector the embedder would never produce: if the backfill re-embedded
        // this row, the sentinel would be overwritten with the recomputed vector.
        float[] sentinel = new float[384];
        sentinel[0] = 42f;
        Dataset embedded = new Dataset("already-embedded", ownerId, null, "unchanging words", null, null);
        embedded.setEmbedding(sentinel);
        UUID embeddedId = repository.save(embedded).getId();
        // A missing row alongside it proves the backfill actually ran and chose to skip.
        UUID missing = repository
                .save(new Dataset("legacy-missing", ownerId, null, null, null, null))
                .getId();

        postBackfill(token);

        assertThat(embeddingOf(missing)).isEqualTo(fake.embed("legacy-missing"));
        assertThat(embeddingOf(embeddedId)).isEqualTo(sentinel);
    }

    @Test
    void backfillRequiresAuthentication() {
        // Even when exposed, the management endpoint sits behind the same JWT wall as the API.
        ResponseEntity<Map> response = rest.postForEntity("/embeddings", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<Map> postBackfill(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map> response =
                rest.exchange("/embeddings", HttpMethod.POST, new HttpEntity<>(null, headers), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private float[] embeddingOf(UUID id) {
        return repository.findById(id).orElseThrow().getEmbedding();
    }

    private UUID insertUser() {
        return jdbc.queryForObject(
                "insert into users (username) values (?) returning id", UUID.class, "backfill-" + UUID.randomUUID());
    }

    private String authedUser() {
        String username = "backfill-caller-" + UUID.randomUUID();
        rest.postForEntity("/v1/auth/register", Map.of("username", username, "password", "pw-12345"), Map.class);
        ResponseEntity<Map> token =
                rest.postForEntity("/v1/auth/token", Map.of("username", username, "password", "pw-12345"), Map.class);
        return (String) token.getBody().get("accessToken");
    }
}
