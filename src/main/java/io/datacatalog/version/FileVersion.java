package io.datacatalog.version;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An immutable file version. Created PENDING when an upload is requested; flips to ACTIVE
 * only once the bytes are confirmed present in object storage (see {@link #activate}).
 */
@Entity
@Table(name = "file_versions")
public class FileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    private String checksum;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VersionState state;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected FileVersion() {
        // for JPA
    }

    public FileVersion(UUID datasetId, int versionNumber, String s3Key) {
        this.datasetId = datasetId;
        this.versionNumber = versionNumber;
        this.s3Key = s3Key;
        this.state = VersionState.PENDING;
    }

    /** Confirm the upload landed: record the server-observed size + checksum and go ACTIVE. */
    public void activate(long sizeBytes, String checksum) {
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
        this.state = VersionState.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDatasetId() {
        return datasetId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getS3Key() {
        return s3Key;
    }

    public VersionState getState() {
        return state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
