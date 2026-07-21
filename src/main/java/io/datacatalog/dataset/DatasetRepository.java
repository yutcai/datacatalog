package io.datacatalog.dataset;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {

    /**
     * Search with optional, AND-combined filters and offset pagination.
     *
     * <p>Every filter is bound as text and made optional via {@code CAST(:p AS ...) IS NULL OR ...}
     * — passing {@code null} skips that predicate. {@code owner} arrives as a UUID string so a null
     * binds cleanly (avoids Postgres' null-UUID parameter type inference). {@code q} matches name or
     * description (case-insensitive substring); {@code tag} uses {@code text[]} containment (GIN).
     *
     * <p>Ordered by {@code (created_at DESC, id DESC)}: the {@code id} tiebreaker makes paging
     * deterministic even when timestamps collide, and is the natural cursor key for a future
     * keyset upgrade.
     */
    @Query(
            value =
                    """
            SELECT * FROM datasets d
            WHERE (CAST(:owner AS uuid) IS NULL OR d.owner_id = CAST(:owner AS uuid))
              AND (CAST(:q AS text) IS NULL
                   OR d.name ILIKE ('%' || :q || '%')
                   OR d.description ILIKE ('%' || :q || '%'))
              AND (CAST(:tag AS text) IS NULL OR d.tags @> ARRAY[CAST(:tag AS text)])
            ORDER BY d.created_at DESC, d.id DESC
            """,
            countQuery =
                    """
            SELECT count(*) FROM datasets d
            WHERE (CAST(:owner AS uuid) IS NULL OR d.owner_id = CAST(:owner AS uuid))
              AND (CAST(:q AS text) IS NULL
                   OR d.name ILIKE ('%' || :q || '%')
                   OR d.description ILIKE ('%' || :q || '%'))
              AND (CAST(:tag AS text) IS NULL OR d.tags @> ARRAY[CAST(:tag AS text)])
            """,
            nativeQuery = true)
    Page<Dataset> search(
            @Param("q") String q, @Param("tag") String tag, @Param("owner") String ownerId, Pageable pageable);
}
