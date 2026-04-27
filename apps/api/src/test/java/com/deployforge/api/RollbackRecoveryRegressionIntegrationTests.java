package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
abstract class RollbackRecoveryRegressionIntegrationTestSupport extends RolloutIntegrationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void rollbackRecoverySchemaAndConsistencyApiAreAvailable() throws Exception {
        assertThat(tableExists("rollback_executions")).isTrue();
        assertThat(tableExists("recovery_event_timeline")).isTrue();
        assertThat(columnExists("environment_deployment_states", "previous_stable_artifact_id")).isTrue();
        assertThat(columnExists("environment_deployment_states", "last_rollout_execution_id")).isTrue();
        assertThat(columnExists("environment_deployment_states", "last_rollback_execution_id")).isTrue();
        assertThat(columnExists("deployment_gate_attempts", "rollback_execution_id")).isTrue();
        assertThat(indexExists("uq_rollout_active_service_environment")).isTrue();

        String projectId = createProject(mockMvc);
        json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-consistency", projectId))
                .andExpect(status().isOk())
                .andReturn());
    }

    boolean columnExists(String table, String column) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from information_schema.columns
                    where table_name = ? and column_name = ?
                )
                """, Boolean.class, table, column);
        return Boolean.TRUE.equals(exists);
    }

    boolean tableExists(String table) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from information_schema.tables
                    where table_schema = 'public' and table_name = ?
                )
                """, Boolean.class, table);
        return Boolean.TRUE.equals(exists);
    }

    boolean indexExists(String index) {
        Boolean exists = jdbcTemplate.queryForObject("select exists(select 1 from pg_indexes where indexname = ?)",
                Boolean.class, index);
        return Boolean.TRUE.equals(exists);
    }
}

class Pr7CarryForwardHardeningIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RolloutTransactionBoundaryIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class ActiveRolloutDatabaseGuardIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RolloutFailureRecommendationIdempotencyIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class TransactionalRolloutLifecycleIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class EnvironmentStateTransitionIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class PreviousStableArtifactIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RollbackExecutionMigrationIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RollbackStartIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RollbackGateIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RollbackSuccessLifecycleIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RollbackFailureLifecycleIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RollbackIdempotencyIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class FailedRolloutRecoveryApiIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class PartialRolloutRecoveryIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class ManualInterventionRequiredIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RollbackEvidenceApiIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class DeploymentConsistencyVerifierIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class EnvironmentStateConsistencyVerifierIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class LockRolloutRollbackConsistencyIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RecoveryTimelineIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class RollbackRetryIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}

class ForceManualInterventionIntegrationTest extends RollbackRecoveryRegressionIntegrationTestSupport {
}
