package io.datacatalog.version;

/** Optional client-computed checksum (MD5 hex) to verify against the stored object's ETag. */
public record CompleteUploadRequest(String checksum) {}
