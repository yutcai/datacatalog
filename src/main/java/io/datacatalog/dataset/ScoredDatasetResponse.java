package io.datacatalog.dataset;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One semantic-search hit: the same fields as {@link DatasetResponse} plus a score. Score is
 * cosine similarity (1 − cosine distance), so higher means closer and identical text scores
 * ~1. A flat record rather than a nested-or-unwrapped {@code DatasetResponse}: the response
 * shape stays readable straight off the record, at the cost of repeating the field list.
 */
public record ScoredDatasetResponse(
        UUID id,
        String name,
        String ownerUsername,
        String team,
        String description,
        List<String> tags,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        double score) {

    static ScoredDatasetResponse of(DatasetResponse dataset, double score) {
        return new ScoredDatasetResponse(
                dataset.id(),
                dataset.name(),
                dataset.ownerUsername(),
                dataset.team(),
                dataset.description(),
                dataset.tags(),
                dataset.metadata(),
                dataset.createdAt(),
                dataset.updatedAt(),
                score);
    }
}
