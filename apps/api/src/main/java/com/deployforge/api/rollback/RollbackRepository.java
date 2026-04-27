package com.deployforge.api.rollback;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.rollout.RollbackRecommendationResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RollbackRepository {
    private final JdbcTemplate jdbcTemplate;

    public RollbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RollbackExecutionResponse create(RollbackRecommendationResponse recommendation, String actor, String reason,
            RollbackStatus status) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into rollback_executions (
                    id, project_id, rollback_recommendation_id, rollout_execution_id, deployment_plan_id,
                    service_id, environment_id, failed_artifact_id, target_artifact_id, status, started_by, reason
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning *
                """, this::map, id, recommendation.projectId(), recommendation.id(), recommendation.rolloutExecutionId(),
                recommendation.deploymentPlanId(), recommendation.serviceId(), recommendation.environmentId(),
                recommendation.failedArtifactId(), recommendation.recommendedArtifactId(), status.name(), actor, reason);
    }

    public Optional<RollbackExecutionResponse> findByRecommendation(UUID projectId, UUID recommendationId) {
        return findOne("select * from rollback_executions where project_id = ? and rollback_recommendation_id = ?", projectId, recommendationId);
    }

    public Optional<RollbackExecutionResponse> findByRollout(UUID projectId, UUID rolloutId) {
        return findOne("select * from rollback_executions where project_id = ? and rollout_execution_id = ? order by created_at desc limit 1",
                projectId, rolloutId);
    }

    public Optional<RollbackExecutionResponse> find(UUID projectId, UUID rollbackId) {
        return findOne("select * from rollback_executions where project_id = ? and id = ?", projectId, rollbackId);
    }

    public RollbackExecutionResponse markSucceeded(UUID rollbackId, String actor, String reason) {
        return jdbcTemplate.queryForObject("""
                update rollback_executions
                set status = 'SUCCEEDED', completed_at = now(), success_actor = ?, success_reason = ?, updated_at = now()
                where id = ?
                returning *
                """, this::map, actor, reason, rollbackId);
    }

    public RollbackExecutionResponse markFailed(UUID rollbackId, String actor, String reason) {
        return jdbcTemplate.queryForObject("""
                update rollback_executions
                set status = 'FAILED', completed_at = now(), failure_actor = ?, failure_reason = ?, updated_at = now()
                where id = ?
                returning *
                """, this::map, actor, reason, rollbackId);
    }

    public RollbackExecutionResponse markManual(UUID rollbackId, String reason) {
        return jdbcTemplate.queryForObject("""
                update rollback_executions
                set status = 'MANUAL_INTERVENTION_REQUIRED', manual_intervention_reason = ?, completed_at = now(), updated_at = now()
                where id = ?
                returning *
                """, this::map, reason, rollbackId);
    }

    public RollbackExecutionResponse retry(UUID rollbackId) {
        return jdbcTemplate.queryForObject("""
                update rollback_executions
                set status = 'RUNNING', retry_count = retry_count + 1, completed_at = null,
                    failure_reason = null, manual_intervention_reason = null, updated_at = now()
                where id = ?
                returning *
                """, this::map, rollbackId);
    }

    private Optional<RollbackExecutionResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::map, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private RollbackExecutionResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new RollbackExecutionResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("rollback_recommendation_id", UUID.class), rs.getObject("rollout_execution_id", UUID.class),
                rs.getObject("deployment_plan_id", UUID.class), rs.getObject("service_id", UUID.class),
                rs.getObject("environment_id", UUID.class), rs.getObject("failed_artifact_id", UUID.class),
                rs.getObject("target_artifact_id", UUID.class), RollbackStatus.valueOf(rs.getString("status")),
                rs.getString("started_by"), rs.getString("reason"), rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class), rs.getString("failure_reason"), rs.getInt("retry_count"),
                rs.getString("manual_intervention_reason"), rs.getString("success_actor"), rs.getString("success_reason"),
                rs.getString("failure_actor"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
