package com.deployforge.api.gate;

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
public class GateExecutionRepository {
    private final JdbcTemplate jdbcTemplate;

    public GateExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int nextAttempt(UUID planId, UUID gateDefinitionId) {
        Integer value = jdbcTemplate.queryForObject("""
                select coalesce(max(attempt_number), 0) + 1 from deployment_gate_attempts
                where deployment_plan_id = ? and gate_definition_id = ?
                """, Integer.class, planId, gateDefinitionId);
        return value == null ? 1 : value;
    }

    public GateAttemptResponse create(UUID projectId, UUID planId, UUID gateDefinitionId, int attemptNumber,
            GateAttemptStatus status, JsonNode observed, String resultSummary, String failureReason) {
        return create(projectId, planId, gateDefinitionId, attemptNumber, status, observed, resultSummary, failureReason, null, null);
    }

    public GateAttemptResponse create(UUID projectId, UUID planId, UUID gateDefinitionId, int attemptNumber,
            GateAttemptStatus status, JsonNode observed, String resultSummary, String failureReason,
            UUID rolloutExecutionId, UUID rolloutWaveId) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_gate_attempts (
                    id, project_id, deployment_plan_id, gate_definition_id, attempt_number, status,
                    observed_json, result_summary, failure_reason, completed_at, rollout_execution_id, rollout_wave_id
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?)
                returning id, project_id, deployment_plan_id, gate_definition_id, attempt_number, status, observed_json::text,
                    result_summary, failure_reason, started_at, completed_at, overridden_at, overridden_by, override_reason
                """, this::map, id, projectId, planId, gateDefinitionId, attemptNumber, status.name(),
                Jsonb.toPgObject(observed), resultSummary, failureReason, rolloutExecutionId, rolloutWaveId);
    }

    public Optional<GateAttemptResponse> find(UUID projectId, UUID attemptId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select id, project_id, deployment_plan_id, gate_definition_id, attempt_number, status, observed_json::text,
                        result_summary, failure_reason, started_at, completed_at, overridden_at, overridden_by, override_reason
                    from deployment_gate_attempts where project_id = ? and id = ?
                    """, this::map, projectId, attemptId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public List<GateAttemptResponse> listByPlan(UUID projectId, UUID planId) {
        return jdbcTemplate.query("""
                select id, project_id, deployment_plan_id, gate_definition_id, attempt_number, status, observed_json::text,
                    result_summary, failure_reason, started_at, completed_at, overridden_at, overridden_by, override_reason
                from deployment_gate_attempts where project_id = ? and deployment_plan_id = ? order by started_at, id
                """, this::map, projectId, planId);
    }

    public List<GateAttemptResponse> listByWave(UUID projectId, UUID rolloutWaveId) {
        return jdbcTemplate.query("""
                select id, project_id, deployment_plan_id, gate_definition_id, attempt_number, status, observed_json::text,
                    result_summary, failure_reason, started_at, completed_at, overridden_at, overridden_by, override_reason
                from deployment_gate_attempts where project_id = ? and rollout_wave_id = ? order by started_at, id
                """, this::map, projectId, rolloutWaveId);
    }

    public GateAttemptResponse override(UUID attemptId, String actor, String reason) {
        return jdbcTemplate.queryForObject("""
                update deployment_gate_attempts
                set status = 'OVERRIDDEN', overridden_at = now(), overridden_by = ?, override_reason = ?
                where id = ?
                returning id, project_id, deployment_plan_id, gate_definition_id, attempt_number, status, observed_json::text,
                    result_summary, failure_reason, started_at, completed_at, overridden_at, overridden_by, override_reason
                """, this::map, actor, reason, attemptId);
    }

    private GateAttemptResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new GateAttemptResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("deployment_plan_id", UUID.class), rs.getObject("gate_definition_id", UUID.class),
                rs.getInt("attempt_number"), GateAttemptStatus.valueOf(rs.getString("status")),
                Jsonb.fromString(rs.getString("observed_json")), rs.getString("result_summary"), rs.getString("failure_reason"),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class),
                rs.getObject("overridden_at", OffsetDateTime.class), rs.getString("overridden_by"), rs.getString("override_reason"));
    }
}
