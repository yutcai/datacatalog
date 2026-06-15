package io.datacatalog.dataset;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

public record CreateDatasetRequest(
        @NotBlank String name,
        String team,
        String description,
        List<String> tags,
        Map<String, Object> metadata) {
}
