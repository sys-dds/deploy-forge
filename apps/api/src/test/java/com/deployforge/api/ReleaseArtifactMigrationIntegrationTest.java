package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class ReleaseArtifactMigrationIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void artifactTablesConstraintsAndIndexesExist() {
        assertThat(tableExists("release_artifacts")).isTrue();
        assertThat(tableExists("release_artifact_evidence")).isTrue();
        assertThat(indexExists("idx_release_artifacts_service_id")).isTrue();
        assertThat(indexExists("idx_release_artifact_evidence_artifact_id")).isTrue();
    }

    @Test
    void artifactConstraintsRejectInvalidRows() {
        UUID projectId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        jdbcTemplate.update("insert into deployment_projects (id, name, slug) values (?, ?, ?)",
                projectId, "Artifact Migration Project", "artifact-migration-" + projectId.toString().substring(0, 8));
        jdbcTemplate.update("insert into deployable_services (id, project_id, name, slug) values (?, ?, ?, ?)",
                serviceId, projectId, "API", "api-" + serviceId.toString().substring(0, 8));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into release_artifacts (id, project_id, service_id, version, git_sha, image_digest, created_by, readiness_status)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, serviceId, " ", "sha", "sha256:x", "dev", "READY"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into release_artifacts (id, project_id, service_id, version, git_sha, image_digest, created_by, readiness_status)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, serviceId, "1", "sha", "sha256:x", "dev", "BAD"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from pg_indexes where schemaname = 'public' and indexname = ?
                """, Integer.class, indexName);
        return count != null && count == 1;
    }
}
