package io.datacatalog.dataset;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
}
