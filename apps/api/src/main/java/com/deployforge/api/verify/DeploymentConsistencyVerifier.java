package com.deployforge.api.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

@Service
public class DeploymentConsistencyVerifier {
    private final JdbcTemplate jdbcTemplate;

    public DeploymentConsistencyVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeploymentConsistencyResponse verify(UUID projectId) {
        List<ConsistencyViolationResponse> violations = new ArrayList<>();
        multipleActiveRollouts(projectId, violations);
        multipleActiveLocks(projectId, violations);
        succeededRolloutCurrentArtifact(projectId, violations);
        failedRolloutCurrentArtifact(projectId, violations);
        rollbackTargetMatchesRecommendation(projectId, violations);
        succeededRollbackCurrentArtifact(projectId, violations);
        runningWavesOnFinalRollout(projectId, violations);
        rolledBackStateHasRollback(projectId, violations);
        deployedStateHasArtifact(projectId, violations);
        return new DeploymentConsistencyResponse(projectId, violations.isEmpty(), violations);
    }

    private void multipleActiveRollouts(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select service_id, environment_id, count(*) as count
                from rollout_executions
                where project_id = ? and status in ('RUNNING','WAITING_FOR_GATES','PAUSED')
                group by service_id, environment_id having count(*) > 1
                """, (RowCallbackHandler) rs -> {
            violations.add(new ConsistencyViolationResponse("MULTIPLE_ACTIVE_ROLLOUTS", "ERROR",
                    "More than one active rollout exists for a service/environment", rs.getObject("service_id", UUID.class),
                    "MANUAL_INTERVENTION"));
        }, projectId);
    }

    private void multipleActiveLocks(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select service_id, environment_id, count(*) as count
                from deployment_locks
                where project_id = ? and status = 'ACTIVE'
                group by service_id, environment_id having count(*) > 1
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("MULTIPLE_ACTIVE_LOCKS", "ERROR",
                "More than one active lock exists for a service/environment", rs.getObject("service_id", UUID.class),
                "RELEASE_STALE_LOCK")), projectId);
    }

    private void succeededRolloutCurrentArtifact(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select r.id
                from rollout_executions r
                join environment_deployment_states s on s.service_id = r.service_id and s.environment_id = r.environment_id
                where r.project_id = ? and r.status = 'SUCCEEDED' and s.current_artifact_id is distinct from r.artifact_id
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("SUCCEEDED_ROLLOUT_ARTIFACT_MISMATCH", "ERROR",
                "Succeeded rollout artifact does not match environment current artifact", rs.getObject("id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void failedRolloutCurrentArtifact(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select r.id
                from rollout_executions r
                join environment_deployment_states s on s.service_id = r.service_id and s.environment_id = r.environment_id
                where r.project_id = ? and r.status = 'FAILED' and s.current_artifact_id = r.artifact_id
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("FAILED_ROLLOUT_MARKED_CURRENT", "ERROR",
                "Failed rollout artifact is marked as current", rs.getObject("id", UUID.class),
                "START_ROLLBACK")), projectId);
    }

    private void rollbackTargetMatchesRecommendation(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select rb.id
                from rollback_executions rb
                join rollback_recommendations rr on rr.id = rb.rollback_recommendation_id
                where rb.project_id = ? and rb.target_artifact_id is distinct from rr.recommended_artifact_id
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("ROLLBACK_TARGET_MISMATCH", "ERROR",
                "Rollback target does not match recommendation target", rs.getObject("id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void succeededRollbackCurrentArtifact(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select rb.id
                from rollback_executions rb
                join environment_deployment_states s on s.service_id = rb.service_id and s.environment_id = rb.environment_id
                where rb.project_id = ? and rb.status = 'SUCCEEDED' and s.current_artifact_id is distinct from rb.target_artifact_id
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("SUCCEEDED_ROLLBACK_ARTIFACT_MISMATCH", "ERROR",
                "Succeeded rollback target does not match environment current artifact", rs.getObject("id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void runningWavesOnFinalRollout(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select r.id
                from rollout_executions r
                join rollout_waves w on w.rollout_execution_id = r.id
                where r.project_id = ? and r.status in ('SUCCEEDED','FAILED','ABORTED') and w.status in ('RUNNING','WAITING_FOR_GATES')
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("RUNNING_WAVE_ON_FINAL_ROLLOUT", "ERROR",
                "Final rollout has a running wave", rs.getObject("id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void rolledBackStateHasRollback(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select service_id
                from environment_deployment_states
                where project_id = ? and state_status = 'ROLLED_BACK' and last_rollback_execution_id is null
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("ROLLED_BACK_WITHOUT_ROLLBACK", "ERROR",
                "Rolled back environment state has no rollback execution", rs.getObject("service_id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void deployedStateHasArtifact(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select service_id
                from environment_deployment_states
                where project_id = ? and state_status = 'DEPLOYED' and current_artifact_id is null
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("DEPLOYED_WITHOUT_ARTIFACT", "ERROR",
                "Deployed environment state has no current artifact", rs.getObject("service_id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }
}
