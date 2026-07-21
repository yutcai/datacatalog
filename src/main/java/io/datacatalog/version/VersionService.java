package io.datacatalog.version;

import io.datacatalog.dataset.Dataset;
import io.datacatalog.dataset.DatasetRepository;
import io.datacatalog.storage.StorageService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

@Service
public class VersionService {

    private final DatasetRepository datasets;
    private final FileVersionRepository versions;
    private final StorageService storage;

    public VersionService(DatasetRepository datasets, FileVersionRepository versions, StorageService storage) {
        this.datasets = datasets;
        this.versions = versions;
        this.storage = storage;
    }

    /** List a dataset's ACTIVE versions, newest first. PENDING uploads stay invisible to reads. */
    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(UUID datasetId) {
        if (!datasets.existsById(datasetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found");
        }
        return versions.findByDatasetIdAndStateOrderByVersionNumberDesc(datasetId, VersionState.ACTIVE).stream()
                .map(VersionResponse::of)
                .toList();
    }

    /** Step 1: register a PENDING version and hand back a pre-signed PUT URL. */
    @Transactional
    public RequestUploadResponse requestUpload(UUID datasetId) {
        if (!datasets.existsById(datasetId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found");
        }
        int versionNumber = versions.maxVersionNumber(datasetId) + 1;
        String s3Key = "datasets/" + datasetId + "/versions/" + UUID.randomUUID();

        FileVersion saved = versions.saveAndFlush(new FileVersion(datasetId, versionNumber, s3Key));
        String uploadUrl = storage.presignPut(s3Key).toString();

        return new RequestUploadResponse(saved.getId(), saved.getVersionNumber(), s3Key, uploadUrl);
    }

    /**
     * Step 2: the server can't observe the direct-to-S3 PUT, so it confirms the object is
     * actually there (HEAD) before flipping PENDING -> ACTIVE and recording its size/checksum.
     */
    @Transactional
    public VersionResponse complete(UUID datasetId, UUID versionId, CompleteUploadRequest request) {
        FileVersion version = versions.findByIdAndDatasetId(versionId, datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "version not found"));
        if (version.getState() != VersionState.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "version is not pending");
        }

        HeadObjectResponse head = storage.head(version.getS3Key())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "uploaded object not found in storage"));

        String etag = head.eTag() == null ? null : head.eTag().replace("\"", "");
        if (request != null
                && request.checksum() != null
                && etag != null
                && !request.checksum().equalsIgnoreCase(etag)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "checksum mismatch");
        }

        version.activate(head.contentLength(), etag);

        Dataset dataset = datasets.findById(datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        dataset.setLatestVersionId(version.getId());

        // Seam: this is where a dataset.version.activated domain event is published in Phase 1
        // (via a transactional outbox, in this same transaction). Nothing async runs inline here.

        return VersionResponse.of(version);
    }

    /** Hand back a pre-signed GET URL — only for ACTIVE versions. */
    @Transactional(readOnly = true)
    public DownloadResponse download(UUID datasetId, UUID versionId) {
        FileVersion version = versions.findByIdAndDatasetId(versionId, datasetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "version not found"));
        if (version.getState() != VersionState.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "version is not active");
        }
        return new DownloadResponse(storage.presignGet(version.getS3Key()).toString());
    }
}
