package io.datacatalog.dataset;

import java.util.List;

/** One page of search results: the items plus the offset-pagination envelope. */
public record DatasetPage(List<DatasetResponse> items, int page, int limit, long total) {}
