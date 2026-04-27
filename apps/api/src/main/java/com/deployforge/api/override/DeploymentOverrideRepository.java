package com.deployforge.api.override;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeploymentOverrideRepository {
    private final JdbcTemplate jdbcTemplate;

    public DeploymentOverrideRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeploymentOverrideResponse create(UUID projectId, UUID planId, CreateDeploymentOverrideRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_overrides (
                    id, project_id, deployment_plan_id, override_type, actor, reason, risk_acknowledgement, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, deployment_plan_id, override_type, actor, reason, risk_acknowledgement, metadata_json::text, created_at
                """, this::map, id, projectId, planId, request.overrideType().name(), request.actor(), request.reason(),
                request.riskAcknowledgement(), Jsonb.toPgObject(request.metadata()));
    }

    public List<DeploymentOverrideResponse> list(UUID projectId, UUID planId) {
        return jdbcTemplate.query("""
                select id, project_id, deployment_plan_id, override_type, actor, reason, risk_acknowledgement, metadata_json::text, created_at
                from deployment_overrides where project_id = ? and deployment_plan_id = ? order by created_at, id
                """, this::map, projectId, planId);
    }

    public boolean exists(UUID projectId, UUID planId, DeploymentOverrideType type) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(select 1 from deployment_overrides where project_id = ? and deployment_plan_id = ? and override_type = ?)
                """, Boolean.class, projectId, planId, type.name());
        return Boolean.TRUE.equals(exists);
    }

    private DeploymentOverrideResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new DeploymentOverrideResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("deployment_plan_id", UUID.class), DeploymentOverrideType.valueOf(rs.getString("override_type")),
                rs.getString("actor"), rs.getString("reason"), rs.getString("risk_acknowledgement"),
                Jsonb.fromString(rs.getString("metadata_json")), rs.getObject("created_at", OffsetDateTime.class));
    }
}
