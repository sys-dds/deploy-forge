package com.deployforge.api.plan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeploymentPlanRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeploymentPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeploymentPlanResponse create(UUID projectId, CreateDeploymentPlanRequest request, String idempotencyKey,
            String requestHash, RiskCalculation risk, JsonNode evidenceSnapshot) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_plans (
                    id, project_id, service_id, artifact_id, target_environment_id, strategy, status,
                    risk_level, reason, requested_by, idempotency_key, request_hash, evidence_snapshot_json
                )
                values (?, ?, ?, ?, ?, ?, 'READY', ?, ?, ?, ?, ?, ?)
                returning id, project_id, service_id, artifact_id, target_environment_id, strategy, status,
                    risk_level, reason, requested_by, idempotency_key, request_hash,
                    evidence_snapshot_json::text, created_at, updated_at, cancelled_at, cancelled_by, cancel_reason
                """, this::mapPlan, id, projectId, request.serviceId(), request.artifactId(),
                request.targetEnvironmentId(), request.strategy().name(), risk.riskLevel().name(),
                request.reason(), request.requestedBy(), idempotencyKey, requestHash, Jsonb.toPgObject(evidenceSnapshot));
    }

    public Optional<DeploymentPlanResponse> findByProjectAndIdempotencyKey(UUID projectId, String idempotencyKey) {
        return findOne("""
                select id, project_id, service_id, artifact_id, target_environment_id, strategy, status,
                    risk_level, reason, requested_by, idempotency_key, request_hash,
                    evidence_snapshot_json::text, created_at, updated_at, cancelled_at, cancelled_by, cancel_reason
                from deployment_plans
                where project_id = ? and idempotency_key = ?
                """, projectId, idempotencyKey);
    }

    public Optional<DeploymentPlanResponse> findById(UUID projectId, UUID planId) {
        return findOne("""
                select id, project_id, service_id, artifact_id, target_environment_id, strategy, status,
                    risk_level, reason, requested_by, idempotency_key, request_hash,
                    evidence_snapshot_json::text, created_at, updated_at, cancelled_at, cancelled_by, cancel_reason
                from deployment_plans
                where project_id = ? and id = ?
                """, projectId, planId);
    }

    public List<DeploymentPlanResponse> findByProject(UUID projectId) {
        return jdbcTemplate.query("""
                select id, project_id, service_id, artifact_id, target_environment_id, strategy, status,
                    risk_level, reason, requested_by, idempotency_key, request_hash,
                    evidence_snapshot_json::text, created_at, updated_at, cancelled_at, cancelled_by, cancel_reason
                from deployment_plans
                where project_id = ?
                order by created_at, id
                """, this::mapPlan, projectId);
    }

    public DeploymentPlanResponse cancel(DeploymentPlanResponse plan, CancelDeploymentPlanRequest request) {
        return jdbcTemplate.queryForObject("""
                update deployment_plans
                set status = 'CANCELLED',
                    cancelled_at = now(),
                    cancelled_by = ?,
                    cancel_reason = ?,
                    updated_at = now()
                where id = ?
                returning id, project_id, service_id, artifact_id, target_environment_id, strategy, status,
                    risk_level, reason, requested_by, idempotency_key, request_hash,
                    evidence_snapshot_json::text, created_at, updated_at, cancelled_at, cancelled_by, cancel_reason
                """, this::mapPlan, request.cancelledBy(), request.reason(), plan.id());
    }

    public DeploymentPlanResponse abort(DeploymentPlanResponse plan, AbortDeploymentPlanRequest request) {
        return jdbcTemplate.queryForObject("""
                update deployment_plans
                set status = 'ABORTED',
                    aborted_at = now(),
                    aborted_by = ?,
                    abort_reason = ?,
                    updated_at = now()
                where id = ?
                returning id, project_id, service_id, artifact_id, target_environment_id, strategy, status,
                    risk_level, reason, requested_by, idempotency_key, request_hash,
                    evidence_snapshot_json::text, created_at, updated_at, cancelled_at, cancelled_by, cancel_reason
                """, this::mapPlan, request.abortedBy(), request.reason(), plan.id());
    }

    public int countByProjectAndKey(UUID projectId, String idempotencyKey) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from deployment_plans where project_id = ? and idempotency_key = ?",
                Integer.class,
                projectId,
                idempotencyKey
        );
        return count == null ? 0 : count;
    }

    private Optional<DeploymentPlanResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapPlan, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private DeploymentPlanResponse mapPlan(ResultSet rs, int rowNum) throws SQLException {
        return new DeploymentPlanResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("service_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                rs.getObject("target_environment_id", UUID.class),
                DeploymentStrategy.valueOf(rs.getString("strategy")),
                DeploymentPlanStatus.valueOf(rs.getString("status")),
                RiskLevel.valueOf(rs.getString("risk_level")),
                rs.getString("reason"),
                rs.getString("requested_by"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                Jsonb.fromString(rs.getString("evidence_snapshot_json")),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("cancelled_at", OffsetDateTime.class),
                rs.getString("cancelled_by"),
                rs.getString("cancel_reason")
        );
    }
}
