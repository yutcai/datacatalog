package io.datacatalog.dataset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "datasets")
public class Dataset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    private String team;

    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Column(name = "latest_version_id")
    private UUID latestVersionId;

    // Populated by the database default (now()) and read back after insert.
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Re-read on UPDATE too: a BEFORE UPDATE trigger refreshes updated_at in the DB.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected Dataset() {
        // for JPA
    }

    public Dataset(
            String name,
            UUID ownerId,
            String team,
            String description,
            List<String> tags,
            Map<String, Object> metadata) {
        this.name = name;
        this.ownerId = ownerId;
        this.team = team;
        this.description = description;
        if (tags != null) {
            this.tags = tags;
        }
        if (metadata != null) {
            this.metadata = metadata;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTeam() {
        return team;
    }

    public void setTeam(String team) {
        this.team = team;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = (tags == null) ? new ArrayList<>() : tags;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Merge the given keys into the existing metadata (overwrite/add; unspecified keys kept).
     * Assigns a new map instance so Hibernate detects the JSONB column as dirty.
     */
    public void mergeMetadata(Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>(this.metadata == null ? Map.of() : this.metadata);
        merged.putAll(incoming);
        this.metadata = merged;
    }

    public UUID getLatestVersionId() {
        return latestVersionId;
    }

    public void setLatestVersionId(UUID latestVersionId) {
        this.latestVersionId = latestVersionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
