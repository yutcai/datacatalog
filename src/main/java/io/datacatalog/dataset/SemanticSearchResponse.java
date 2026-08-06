package io.datacatalog.dataset;

import java.util.List;

/**
 * A semantic search result: the k nearest datasets, best first. Deliberately not a
 * {@link DatasetPage} — a top-k ranking has no offset to resume from, so there is no
 * page / limit / total envelope, just the ranked items with scores.
 */
public record SemanticSearchResponse(List<ScoredDatasetResponse> items) {}
