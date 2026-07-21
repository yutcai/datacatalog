package io.datacatalog.dataset;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

public record CreateDatasetRequest(
        @NotBlank String name, String team, String description, List<String> tags, Map<String, Object> metadata) {}
