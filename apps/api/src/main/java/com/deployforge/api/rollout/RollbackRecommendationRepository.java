package com.deployforge.api.rollout;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RollbackRecommendationRepository {
    private final JdbcTemplate jdbcTemplate;

    public RollbackRecommendationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RollbackRecommendationResponse create(RolloutExecutionResponse rollout, UUID recommendedArtifactId, String reason) {
        return findOpen(rollout.projectId(), rollout.id()).orElseGet(() -> jdbcTemplate.queryForObject("""
                insert into rollback_recommendations (
                    id, project_id, rollout_execution_id, deployment_plan_id, service_id, environment_id,
                    failed_artifact_id, recommended_artifact_id, recommendation_status, reason
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?)
                returning *
                """, this::map, UUID.randomUUID(), rollout.projectId(), rollout.id(), rollout.deploymentPlanId(),
                rollout.serviceId(), rollout.environmentId(), rollout.artifactId(), recommendedArtifactId, reason));
    }

    public Optional<RollbackRecommendationResponse> findOpen(UUID projectId, UUID rolloutId) {
        return findOne("""
                select * from rollback_recommendations
                where project_id = ? and rollout_execution_id = ? and recommendation_status = 'OPEN'
                """, projectId, rolloutId);
    }

    public Optional<RollbackRecommendationResponse> find(UUID projectId, UUID rolloutId) {
        return findOne("select * from rollback_recommendations where project_id = ? and rollout_execution_id = ? order by created_at desc limit 1",
                projectId, rolloutId);
    }

    public Optional<RollbackRecommendationResponse> findById(UUID projectId, UUID recommendationId) {
        return findOne("select * from rollback_recommendations where project_id = ? and id = ?", projectId, recommendationId);
    }

    public RollbackRecommendationResponse acknowledge(UUID recommendationId, AcknowledgeRollbackRecommendationRequest request) {
        return jdbcTemplate.queryForObject("""
                update rollback_recommendations
                set recommendation_status = 'ACKNOWLEDGED',
                    acknowledged_at = now(),
                    acknowledged_by = ?,
                    acknowledgement_reason = ?
                where id = ?
                returning *
                """, this::map, request.acknowledgedBy(), request.reason(), recommendationId);
    }

    private Optional<RollbackRecommendationResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::map, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private RollbackRecommendationResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new RollbackRecommendationResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("rollout_execution_id", UUID.class), rs.getObject("deployment_plan_id", UUID.class),
                rs.getObject("service_id", UUID.class), rs.getObject("environment_id", UUID.class),
                rs.getObject("failed_artifact_id", UUID.class), rs.getObject("recommended_artifact_id", UUID.class),
                RollbackRecommendationStatus.valueOf(rs.getString("recommendation_status")), rs.getString("reason"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("acknowledged_at", OffsetDateTime.class),
                rs.getString("acknowledged_by"), rs.getString("acknowledgement_reason"));
    }
}
