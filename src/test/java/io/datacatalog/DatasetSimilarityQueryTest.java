package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.datacatalog.dataset.CreateDatasetRequest;
import io.datacatalog.dataset.Dataset;
import io.datacatalog.dataset.DatasetRepository;
import io.datacatalog.dataset.DatasetService;
import io.datacatalog.dataset.NearestDataset;
import io.datacatalog.embedding.FakeEmbeddingClient;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The similarity query ranks datasets by cosine distance between their stored embedding and
 * a query vector — against real pgvector, through the deterministic fake embedder, which
 * makes the expected order computable from text alone: sharing more tokens with the query
 * means landing closer. The database is shared across test classes, so assertions filter to
 * the rows this test created instead of claiming absolute positions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DatasetSimilarityQueryTest {

    // More rows than the shared database holds during a test run: absence from a result this
    // large proves a row was filtered out, not truncated away by the limit.
    private static final int ALL_ROWS = 10_000;

    @Autowired
    private DatasetService service;

    @Autowired
    private DatasetRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    // The write path embeds through the same deterministic fake, so query vectors built here
    // live in the same space as the stored embeddings.
    private final FakeEmbeddingClient fake = new FakeEmbeddingClient();

    @Test
    void ranksDatasetsByCosineSimilarityToTheQuery() {
        String username = insertUsername();
        // Distinctive tokens no other test uses; the three names share 3, 2 and 1 of the
        // query's tokens, so their similarity to it strictly decreases in that order.
        UUID sharesAll = createNamed(username, "zephyr quokka obsidian");
        UUID sharesTwo = createNamed(username, "zephyr quokka");
        UUID sharesOne = createNamed(username, "zephyr");
        Set<UUID> mine = Set.of(sharesAll, sharesTwo, sharesOne);

        List<NearestDataset> ranked =
                repository.findNearest(fake.embed("zephyr quokka obsidian"), PageRequest.of(0, ALL_ROWS)).stream()
                        .filter(hit -> mine.contains(hit.dataset().getId()))
                        .toList();

        assertThat(ranked).extracting(hit -> hit.dataset().getId()).containsExactly(sharesAll, sharesTwo, sharesOne);
        // The reported distance is what the ranking ordered by: identical text sits at
        // distance ~0 and each dropped token pushes the distance strictly up.
        assertThat(ranked.get(0).distance()).isCloseTo(0.0, within(1e-6));
        assertThat(ranked)
                .extracting(NearestDataset::distance)
                .isSortedAccordingTo(Comparator.naturalOrder())
                .doesNotHaveDuplicates();
    }

    @Test
    void neverReturnsRowsWithoutAnEmbedding() {
        UUID ownerId = insertUserReturningId();
        // Saved through the repository, bypassing the write path — so the embedding is NULL,
        // like a row that pre-dates the embedding feature and hasn't been backfilled.
        UUID unembedded = repository
                .save(new Dataset("null-embedding-probe", ownerId, null, null, null, null))
                .getId();

        List<UUID> ids = repository.findNearest(fake.embed("zephyr"), PageRequest.of(0, ALL_ROWS)).stream()
                .map(hit -> hit.dataset().getId())
                .toList();

        assertThat(ids).doesNotContain(unembedded);
    }

    @Test
    void returnsAtMostKRows() {
        String username = insertUsername();
        createNamed(username, "kelp krill kraken");
        createNamed(username, "kelp krill");
        createNamed(username, "kelp");

        assertThat(repository.findNearest(fake.embed("kelp"), PageRequest.of(0, 2)))
                .hasSize(2);
    }

    private UUID createNamed(String username, String name) {
        return service.create(username, new CreateDatasetRequest(name, null, null, null, null))
                .id();
    }

    private String insertUsername() {
        String username = "similarity-" + UUID.randomUUID();
        jdbc.update("insert into users (username) values (?)", username);
        return username;
    }

    private UUID insertUserReturningId() {
        return jdbc.queryForObject(
                "insert into users (username) values (?) returning id", UUID.class, "similarity-" + UUID.randomUUID());
    }
}
