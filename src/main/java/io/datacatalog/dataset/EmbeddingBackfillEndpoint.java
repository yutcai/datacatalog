package io.datacatalog.dataset;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backfills embeddings for rows that pre-date the write-path embedding (or were never
 * embedded for any other reason).
 *
 * <p>This is a management operation, not part of the public API: the project has no admin
 * role yet, so instead of a {@code /v1} route it is an actuator write operation ({@code
 * POST /embeddings}) that ships dark — the default exposure includes only {@code health}.
 * An operator enables it on demand ({@code
 * management.endpoints.web.exposure.include=health,embeddings}), runs the backfill, and
 * turns it back off. Even exposed it sits behind the same JWT authentication as the API.
 *
 * <p>Only rows with no embedding are touched, so the operation is idempotent: a second run
 * reports zero. Re-embedding <em>everything</em> (needed if the embedding provider ever
 * changes, since vectors from different models are not comparable) is deliberately out of
 * scope until a real provider lands.
 */
@Component
@Endpoint(id = "embeddings")
public class EmbeddingBackfillEndpoint {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillEndpoint.class);

    private final DatasetRepository datasets;
    private final DatasetEmbedder embedder;

    public EmbeddingBackfillEndpoint(DatasetRepository datasets, DatasetEmbedder embedder) {
        this.datasets = datasets;
        this.embedder = embedder;
    }

    public record BackfillResult(int backfilled) {}

    @WriteOperation
    @Transactional
    public BackfillResult backfill() {
        List<Dataset> missing = datasets.findAllByEmbeddingIsNull();
        missing.forEach(embedder::embed);
        // Operator-triggered action: confirm the outcome in the application log too, not
        // only in an HTTP response nobody may have captured.
        log.info("Embeddings backfill filled {} dataset(s)", missing.size());
        // The loaded entities are managed within this transaction; the new vectors flush on
        // commit. One transaction for the whole run is fine at catalog scale — batching
        // becomes worthwhile only with a real (slow) provider and row counts to match.
        return new BackfillResult(missing.size());
    }
}
