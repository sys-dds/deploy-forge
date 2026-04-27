package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class FoundationMigrationIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesFoundationTablesAndIndexes() {
        assertThat(tableExists("deployment_projects")).isTrue();
        assertThat(tableExists("deployable_services")).isTrue();
        assertThat(tableExists("deployment_environments")).isTrue();
        assertThat(indexExists("idx_deployable_services_project_id")).isTrue();
        assertThat(indexExists("idx_deployment_environments_project_id")).isTrue();
        assertThat(indexExists("idx_deployment_environments_project_id_sort_order")).isTrue();
    }

    @Test
    void environmentTypeConstraintRejectsInvalidValues() {
        UUID projectId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into deployment_projects (id, name, slug)
                values (?, ?, ?)
                """, projectId, "Constraint Project", "constraint-project");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into deployment_environments (
                    id, project_id, name, environment_type, protected_environment, sort_order
                )
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, "invalid", "INVALID", false, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void projectSlugConstraintRejectsDuplicates() {
        jdbcTemplate.update("""
                insert into deployment_projects (id, name, slug)
                values (?, ?, ?)
                """, UUID.randomUUID(), "Unique Project One", "unique-project");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into deployment_projects (id, name, slug)
                values (?, ?, ?)
                """, UUID.randomUUID(), "Unique Project Two", "unique-project"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_name = ?
                """, Integer.class, tableName);
        return count != null && count == 1;
    }

    private boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from pg_indexes
                where schemaname = 'public'
                  and indexname = ?
                """, Integer.class, indexName);
        return count != null && count == 1;
    }
}
