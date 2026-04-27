package com.deployforge.api.policy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.plan.RiskLevel;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProtectedEnvironmentPolicyRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProtectedEnvironmentPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProtectedEnvironmentPolicyResponse upsert(UUID projectId, UUID environmentId, UpsertProtectedEnvironmentPolicyRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into protected_environment_policies (
                    id, project_id, environment_id, require_approval, required_approval_count,
                    require_promotion_evidence, allow_emergency_override, max_risk_without_override, created_by
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (project_id, environment_id) do update
                set require_approval = excluded.require_approval,
                    required_approval_count = excluded.required_approval_count,
                    require_promotion_evidence = excluded.require_promotion_evidence,
                    allow_emergency_override = excluded.allow_emergency_override,
                    max_risk_without_override = excluded.max_risk_without_override,
                    created_by = excluded.created_by,
                    updated_at = now()
                returning id, project_id, environment_id, require_approval, required_approval_count,
                    require_promotion_evidence, allow_emergency_override, max_risk_without_override, created_by, created_at, updated_at
                """, this::map, id, projectId, environmentId, bool(request.requireApproval(), false),
                request.requiredApprovalCount() == null ? 1 : request.requiredApprovalCount(),
                bool(request.requirePromotionEvidence(), false), bool(request.allowEmergencyOverride(), true),
                (request.maxRiskWithoutOverride() == null ? RiskLevel.MEDIUM : request.maxRiskWithoutOverride()).name(),
                request.createdBy());
    }

    public Optional<ProtectedEnvironmentPolicyResponse> find(UUID projectId, UUID environmentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select id, project_id, environment_id, require_approval, required_approval_count,
                        require_promotion_evidence, allow_emergency_override, max_risk_without_override, created_by, created_at, updated_at
                    from protected_environment_policies where project_id = ? and environment_id = ?
                    """, this::map, projectId, environmentId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private ProtectedEnvironmentPolicyResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new ProtectedEnvironmentPolicyResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("environment_id", UUID.class), rs.getBoolean("require_approval"),
                rs.getInt("required_approval_count"), rs.getBoolean("require_promotion_evidence"),
                rs.getBoolean("allow_emergency_override"), RiskLevel.valueOf(rs.getString("max_risk_without_override")),
                rs.getString("created_by"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private boolean bool(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
