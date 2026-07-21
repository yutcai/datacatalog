package io.datacatalog.dataset;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DatasetResponse(
        UUID id,
        String name,
        String ownerUsername,
        String team,
        String description,
        List<String> tags,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
