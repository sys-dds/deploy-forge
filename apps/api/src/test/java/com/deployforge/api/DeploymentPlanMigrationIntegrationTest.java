package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DeploymentPlanMigrationIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void planStateAndEventTablesExist() {
        assertThat(tableExists("deployment_plans")).isTrue();
        assertThat(tableExists("environment_deployment_states")).isTrue();
        assertThat(tableExists("deployment_intent_events")).isTrue();
        assertThat(indexExists("idx_deployment_plans_project_id")).isTrue();
        assertThat(indexExists("idx_deployment_intent_events_project_id_created_at")).isTrue();
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
