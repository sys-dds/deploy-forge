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
        rollbackScopeMatchesRollout(projectId, violations);
        rollbackArtifactBelongsToService(projectId, violations);
        environmentArtifactBelongsToService(projectId, violations);
        rolledBackStateHasSuccessfulRollback(projectId, violations);
        manualInterventionHasReason(projectId, violations);
        terminalRolloutNoActiveLock(projectId, violations);
        rollbackHasRecommendation(projectId, violations);
        desiredStateChecks(projectId, violations);
        actualMismatchNeedsOpenDrift(projectId, violations);
        staleTargetNeedsOpenDrift(projectId, violations);
        expiredCommandLeaseReported(projectId, violations);
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

    private void rollbackScopeMatchesRollout(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select rb.id
                from rollback_executions rb
                join rollout_executions r on r.id = rb.rollout_execution_id
                where rb.project_id = ? and (rb.service_id <> r.service_id or rb.environment_id <> r.environment_id
                    or rb.deployment_plan_id <> r.deployment_plan_id or rb.project_id <> r.project_id)
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("ROLLBACK_SCOPE_MISMATCH", "ERROR",
                "Rollback execution does not match failed rollout scope", rs.getObject("id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void rollbackArtifactBelongsToService(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select rb.id
                from rollback_executions rb
                left join release_artifacts a on a.id = rb.target_artifact_id
                where rb.project_id = ? and rb.target_artifact_id is not null
                    and (a.project_id is distinct from rb.project_id or a.service_id is distinct from rb.service_id)
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("ROLLBACK_TARGET_ARTIFACT_SCOPE_MISMATCH", "ERROR",
                "Rollback target artifact is outside rollback service/project", rs.getObject("id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void environmentArtifactBelongsToService(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select s.service_id
                from environment_deployment_states s
                left join release_artifacts a on a.id = s.current_artifact_id
                where s.project_id = ? and s.current_artifact_id is not null
                    and (a.project_id is distinct from s.project_id or a.service_id is distinct from s.service_id)
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("ENVIRONMENT_ARTIFACT_SCOPE_MISMATCH", "ERROR",
                "Environment current artifact is outside service/project", rs.getObject("service_id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void rolledBackStateHasSuccessfulRollback(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select s.service_id
                from environment_deployment_states s
                left join rollback_executions rb on rb.id = s.last_rollback_execution_id and rb.status = 'SUCCEEDED'
                where s.project_id = ? and s.state_status = 'ROLLED_BACK' and rb.id is null
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("ROLLED_BACK_WITHOUT_SUCCESSFUL_ROLLBACK", "ERROR",
                "Rolled back state must have a successful rollback execution", rs.getObject("service_id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void manualInterventionHasReason(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select s.service_id
                from environment_deployment_states s
                left join rollback_executions rb on rb.id = s.last_rollback_execution_id
                where s.project_id = ? and s.state_status = 'MANUAL_INTERVENTION_REQUIRED'
                    and coalesce(rb.manual_intervention_reason, rb.failure_reason, '') = ''
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("MANUAL_INTERVENTION_WITHOUT_REASON", "ERROR",
                "Manual intervention state needs a recorded rollback reason", rs.getObject("service_id", UUID.class),
                "RECORD_RECOVERY_ACTION")), projectId);
    }

    private void terminalRolloutNoActiveLock(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select r.id
                from rollout_executions r
                join deployment_locks l on l.deployment_plan_id = r.deployment_plan_id and l.status = 'ACTIVE'
                left join environment_deployment_states s on s.service_id = r.service_id and s.environment_id = r.environment_id
                where r.project_id = ? and r.status in ('SUCCEEDED','FAILED','ABORTED')
                    and coalesce(s.state_status, '') <> 'MANUAL_INTERVENTION_REQUIRED'
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("TERMINAL_ROLLOUT_ACTIVE_LOCK", "ERROR",
                "Terminal rollout still has an active deployment lock", rs.getObject("id", UUID.class),
                "RELEASE_STALE_LOCK")), projectId);
    }

    private void rollbackHasRecommendation(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select rb.id
                from rollback_executions rb
                left join rollback_recommendations rr on rr.id = rb.rollback_recommendation_id
                where rb.project_id = ? and rr.id is null
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("ROLLBACK_WITHOUT_RECOMMENDATION", "ERROR",
                "Rollback execution has no recommendation", rs.getObject("id", UUID.class),
                "MANUAL_INTERVENTION")), projectId);
    }

    private void desiredStateChecks(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select d.id
                from desired_environment_states d
                left join release_artifacts a on a.id = d.desired_artifact_id
                where d.project_id = ? and d.desired_artifact_id is not null
                    and (a.project_id is distinct from d.project_id or a.service_id is distinct from d.service_id)
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("DESIRED_ARTIFACT_SCOPE_MISMATCH", "ERROR",
                "Desired artifact is outside service/project", rs.getObject("id", UUID.class),
                "RECORD_DESIRED_STATE")), projectId);
    }

    private void actualMismatchNeedsOpenDrift(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select t.id
                from runtime_targets t
                join desired_environment_states d on d.service_id = t.service_id and d.environment_id = t.environment_id
                join runtime_deployment_reports r on r.id = (
                    select r2.id from runtime_deployment_reports r2
                    where r2.runtime_target_id = t.id order by r2.observed_at desc, r2.created_at desc limit 1
                )
                where t.project_id = ? and r.report_status = 'RUNNING'
                    and d.desired_image_digest is distinct from r.reported_image_digest
                    and not exists (
                        select 1 from drift_findings f
                        where f.project_id = t.project_id and f.service_id = t.service_id and f.environment_id = t.environment_id
                            and f.runtime_target_id = t.id and f.status in ('OPEN','ACKNOWLEDGED')
                    )
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("ACTUAL_MISMATCH_WITHOUT_OPEN_DRIFT", "ERROR",
                "Actual runtime state mismatches desired state without an open drift finding", rs.getObject("id", UUID.class),
                "RUN_DRIFT_CHECK")), projectId);
    }

    private void staleTargetNeedsOpenDrift(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select t.id
                from runtime_targets t
                left join runtime_target_heartbeats h on h.id = (
                    select h2.id from runtime_target_heartbeats h2
                    where h2.runtime_target_id = t.id order by h2.heartbeat_at desc, h2.created_at desc limit 1
                )
                where t.project_id = ? and t.status = 'ACTIVE'
                    and (h.id is null or h.heartbeat_at < now() - interval '300 seconds')
                    and not exists (
                        select 1 from drift_findings f
                        where f.project_id = t.project_id and f.runtime_target_id = t.id
                            and f.drift_type = 'STALE_TARGET_REPORT' and f.status in ('OPEN','ACKNOWLEDGED')
                    )
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("STALE_TARGET_WITHOUT_OPEN_DRIFT", "WARNING",
                "Stale runtime target does not have an open stale-target drift finding", rs.getObject("id", UUID.class),
                "RUN_DRIFT_CHECK")), projectId);
    }

    private void expiredCommandLeaseReported(UUID projectId, List<ConsistencyViolationResponse> violations) {
        jdbcTemplate.query("""
                select id
                from deployment_commands
                where project_id = ? and status in ('LEASED','RUNNING') and lease_expires_at < now()
                """, (RowCallbackHandler) rs -> violations.add(new ConsistencyViolationResponse("EXPIRED_COMMAND_LEASE", "WARNING",
                "Active command has an expired lease and needs runner takeover or operator recovery", rs.getObject("id", UUID.class),
                "CLAIM_OR_FORCE_RELEASE_STALE_LEASE")), projectId);
    }
}
