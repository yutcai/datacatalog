package io.datacatalog.version;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VersionResponse(
        UUID id,
        UUID datasetId,
        int versionNumber,
        String state,
        Long sizeBytes,
        String checksum,
        OffsetDateTime createdAt) {

    static VersionResponse of(FileVersion v) {
        return new VersionResponse(v.getId(), v.getDatasetId(), v.getVersionNumber(),
                v.getState().name(), v.getSizeBytes(), v.getChecksum(), v.getCreatedAt());
    }
}
