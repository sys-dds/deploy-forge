package com.deployforge.api.gate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GateDefinitionRepository {
    private final JdbcTemplate jdbcTemplate;

    public GateDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public GateDefinitionResponse create(UUID projectId, CreateGateDefinitionRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_gate_definitions (
                    id, project_id, environment_id, name, gate_type, required, enabled, timeout_seconds, config_json, created_by
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, environment_id, name, gate_type, required, enabled, timeout_seconds, config_json::text,
                    created_by, created_at, updated_at
                """, this::map, id, projectId, request.environmentId(), request.name(), request.gateType().name(),
                request.required() == null || request.required(), request.enabled() == null || request.enabled(),
                request.timeoutSeconds() == null ? 30 : request.timeoutSeconds(), Jsonb.toPgObject(request.config()), request.createdBy());
    }

    public Optional<GateDefinitionResponse> find(UUID projectId, UUID id) {
        return findOne("""
                select id, project_id, environment_id, name, gate_type, required, enabled, timeout_seconds, config_json::text,
                    created_by, created_at, updated_at
                from deployment_gate_definitions where project_id = ? and id = ?
                """, projectId, id);
    }

    public List<GateDefinitionResponse> list(UUID projectId) {
        return jdbcTemplate.query("""
                select id, project_id, environment_id, name, gate_type, required, enabled, timeout_seconds, config_json::text,
                    created_by, created_at, updated_at
                from deployment_gate_definitions where project_id = ? order by created_at, id
                """, this::map, projectId);
    }

    public List<GateDefinitionResponse> enabledForPlan(UUID projectId, UUID environmentId) {
        return jdbcTemplate.query("""
                select id, project_id, environment_id, name, gate_type, required, enabled, timeout_seconds, config_json::text,
                    created_by, created_at, updated_at
                from deployment_gate_definitions
                where project_id = ? and enabled = true and (environment_id is null or environment_id = ?)
                order by created_at, id
                """, this::map, projectId, environmentId);
    }

    private Optional<GateDefinitionResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::map, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private GateDefinitionResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new GateDefinitionResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("environment_id", UUID.class), rs.getString("name"), GateType.valueOf(rs.getString("gate_type")),
                rs.getBoolean("required"), rs.getBoolean("enabled"), rs.getInt("timeout_seconds"),
                Jsonb.fromString(rs.getString("config_json")), rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }
}
