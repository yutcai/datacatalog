package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.datacatalog.dataset.CreateDatasetRequest;
import io.datacatalog.dataset.DatasetRepository;
import io.datacatalog.dataset.DatasetService;
import io.datacatalog.dataset.PatchDatasetRequest;
import io.datacatalog.embedding.FakeEmbeddingClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Embeddings are populated synchronously on the write path: create fills the vector from
 * the human-meaningful text (name + description + tags — not team, not metadata), and
 * PATCH recomputes it only when one of those fields changes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DatasetEmbeddingOnWriteTest {

    @Autowired
    private DatasetService service;

    @Autowired
    private DatasetRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    // The service embeds through the same deterministic fake, so expected vectors can be
    // computed directly from the text the service is supposed to assemble.
    private final FakeEmbeddingClient fake = new FakeEmbeddingClient();

    @Test
    void createPopulatesEmbeddingFromNameDescriptionAndTags() {
        String username = insertUser();

        UUID id = service.create(
                        username,
                        new CreateDatasetRequest(
                                "sales-2026",
                                "finance",
                                "european revenue figures",
                                List.of("sales", "quarterly"),
                                Map.of("rows", 100)))
                .id();

        // team and metadata are deliberately absent from the embedded text.
        float[] expected = fake.embed("sales-2026 european revenue figures sales quarterly");
        assertThat(embeddingOf(id)).isEqualTo(expected);
    }

    @Test
    void createWithOnlyANameEmbedsJustTheName() {
        String username = insertUser();

        UUID id = service.create(username, new CreateDatasetRequest("solo-dataset", null, null, null, null))
                .id();

        // Absent description/tags contribute nothing — no "null" tokens in the text.
        assertThat(embeddingOf(id)).isEqualTo(fake.embed("solo-dataset"));
    }

    @Test
    void patchingAnyEmbeddedFieldRecomputesTheEmbedding() {
        String username = insertUser();
        UUID id = service.create(
                        username,
                        new CreateDatasetRequest("orders-raw", null, "raw order events", List.of("orders"), null))
                .id();

        // Each patch changes one embedded field; the new vector reflects the patched field
        // combined with the untouched ones.
        service.patch(id, username, new PatchDatasetRequest("orders-clean", null, null, null, null));
        assertThat(embeddingOf(id)).isEqualTo(fake.embed("orders-clean raw order events orders"));

        service.patch(id, username, new PatchDatasetRequest(null, null, "deduplicated order events", null, null));
        assertThat(embeddingOf(id)).isEqualTo(fake.embed("orders-clean deduplicated order events orders"));

        service.patch(id, username, new PatchDatasetRequest(null, null, null, List.of("orders", "curated"), null));
        assertThat(embeddingOf(id)).isEqualTo(fake.embed("orders-clean deduplicated order events orders curated"));
    }

    @Test
    void patchWithoutEmbeddedFieldsSkipsReembedding() {
        String username = insertUser();
        UUID id = service.create(
                        username, new CreateDatasetRequest("stable-text", null, "unchanging words", null, null))
                .id();
        // Plant a sentinel vector the embedder would never produce: if the patch below
        // re-embedded, the sentinel would be overwritten with the recomputed vector.
        float[] sentinel = new float[384];
        sentinel[0] = 42f;
        var dataset = repository.findById(id).orElseThrow();
        dataset.setEmbedding(sentinel);
        repository.save(dataset);

        service.patch(id, username, new PatchDatasetRequest(null, "new-team", null, null, Map.of("rows", 7)));

        assertThat(embeddingOf(id)).isEqualTo(sentinel);
    }

    private float[] embeddingOf(UUID id) {
        return repository.findById(id).orElseThrow().getEmbedding();
    }

    private String insertUser() {
        String username = "embed-write-" + UUID.randomUUID();
        jdbc.update("insert into users (username) values (?)", username);
        return username;
    }
}
