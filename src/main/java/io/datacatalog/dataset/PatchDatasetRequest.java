package io.datacatalog.dataset;

import java.util.List;
import java.util.Map;

/**
 * Partial update: any field left null is unchanged. {@code metadata} is merged by key
 * (not replaced); {@code ownerId} and version pointers are never client-editable.
 */
public record PatchDatasetRequest(
        String name,
        String team,
        String description,
        List<String> tags,
        Map<String, Object> metadata) {
}
