package io.datacatalog.version;

import java.util.UUID;

public record RequestUploadResponse(
        UUID versionId,
        int versionNumber,
        String s3Key,
        String uploadUrl) {
}
