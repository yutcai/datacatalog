package io.datacatalog.version;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileVersionRepository extends JpaRepository<FileVersion, UUID> {

    Optional<FileVersion> findByIdAndDatasetId(UUID id, UUID datasetId);

    List<FileVersion> findByDatasetIdAndStateOrderByVersionNumberDesc(UUID datasetId, VersionState state);

    @Query("select coalesce(max(v.versionNumber), 0) from FileVersion v where v.datasetId = :datasetId")
    int maxVersionNumber(@Param("datasetId") UUID datasetId);
}
