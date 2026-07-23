package io.datacatalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.datacatalog.dataset.Dataset;
import io.datacatalog.dataset.DatasetRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class DatasetEmbeddingTest {

    @Autowired
    private DatasetRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void embeddingRoundTripsThroughJpa() {
        UUID ownerId = insertUser("embedding-test-" + UUID.randomUUID());
        float[] embedding = new float[384];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = i / 384f;
        }
        Dataset dataset = new Dataset("embedding-roundtrip", ownerId, null, null, null, null);
        dataset.setEmbedding(embedding);

        UUID id = repository.save(dataset).getId();
        // save() and findById() run in separate transactions here (no @Transactional on the
        // test), so the read below is a genuine reload from Postgres, not a first-level-cache hit.
        Dataset reloaded = repository.findById(id).orElseThrow();

        assertThat(reloaded.getEmbedding()).isEqualTo(embedding);
    }

    @Test
    void embeddingIsNullableForRowsNotYetEmbedded() {
        UUID ownerId = insertUser("embedding-test-" + UUID.randomUUID());
        Dataset dataset = new Dataset("embedding-absent", ownerId, null, null, null, null);

        UUID id = repository.save(dataset).getId();
        Dataset reloaded = repository.findById(id).orElseThrow();

        assertThat(reloaded.getEmbedding()).isNull();
    }

    private UUID insertUser(String username) {
        return jdbc.queryForObject("insert into users (username) values (?) returning id", UUID.class, username);
    }
}
