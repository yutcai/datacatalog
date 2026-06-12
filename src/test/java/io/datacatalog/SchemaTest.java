package io.datacatalog;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void liquibaseCreatesCoreTables() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'",
                String.class);

        assertThat(tables).contains("users", "datasets", "file_versions");
    }

    @Test
    void metadataIsQueryableWithJsonbContainment() {
        UUID ownerId = insertUser("schema-test-alice");
        jdbc.update("""
                insert into datasets (name, owner_id, metadata)
                values (?, ?, ?::jsonb)
                """, "sales-2025", ownerId, "{\"region\": \"emea\", \"format\": \"parquet\"}");

        Integer hits = jdbc.queryForObject("""
                select count(*) from datasets where metadata @> '{"region": "emea"}'::jsonb
                """, Integer.class);

        assertThat(hits).isEqualTo(1);
    }

    @Test
    void metadataAndTagsHaveGinIndexes() {
        List<String> indexDefs = jdbc.queryForList(
                "select indexdef from pg_indexes where tablename = 'datasets'",
                String.class);

        assertThat(indexDefs).anyMatch(def -> def.contains("USING gin") && def.contains("metadata"));
        assertThat(indexDefs).anyMatch(def -> def.contains("USING gin") && def.contains("tags"));
    }

    @Test
    void versionNumberIsUniquePerDataset() {
        UUID ownerId = insertUser("schema-test-bob");
        UUID datasetId = insertDataset("inventory-2025", ownerId);
        insertVersion(datasetId, 1);

        assertThatThrownBy(() -> insertVersion(datasetId, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void versionStateIsRestrictedToPendingOrActive() {
        UUID ownerId = insertUser("schema-test-carol");
        UUID datasetId = insertDataset("logs-2025", ownerId);

        assertThatThrownBy(() -> jdbc.update("""
                insert into file_versions (dataset_id, version_number, s3_key, state)
                values (?, ?, ?, ?)
                """, datasetId, 1, "s3-key", "BOGUS"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertUser(String username) {
        return jdbc.queryForObject(
                "insert into users (username) values (?) returning id", UUID.class, username);
    }

    private UUID insertDataset(String name, UUID ownerId) {
        return jdbc.queryForObject(
                "insert into datasets (name, owner_id) values (?, ?) returning id", UUID.class, name, ownerId);
    }

    private void insertVersion(UUID datasetId, int versionNumber) {
        jdbc.update("""
                insert into file_versions (dataset_id, version_number, s3_key, state)
                values (?, ?, ?, 'PENDING')
                """, datasetId, versionNumber, "datasets/" + datasetId + "/v" + versionNumber);
    }
}
