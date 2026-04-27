package com.deployforge.api.promotion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PromotionRuleRepository {
    private final JdbcTemplate jdbcTemplate;

    public PromotionRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PromotionRuleResponse upsert(UUID projectId, UUID environmentId, UpsertPromotionRuleRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into environment_promotion_rules (
                    id, project_id, target_environment_id, required_source_environment_id,
                    requires_successful_source_deployment, requires_approval, allow_override, created_by
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (project_id, target_environment_id) do update
                set required_source_environment_id = excluded.required_source_environment_id,
                    requires_successful_source_deployment = excluded.requires_successful_source_deployment,
                    requires_approval = excluded.requires_approval,
                    allow_override = excluded.allow_override,
                    created_by = excluded.created_by,
                    updated_at = now()
                returning id, project_id, target_environment_id, required_source_environment_id,
                    requires_successful_source_deployment, requires_approval, allow_override, created_by, created_at, updated_at
                """, this::map, id, projectId, environmentId, request.requiredSourceEnvironmentId(),
                bool(request.requiresSuccessfulSourceDeployment(), true), bool(request.requiresApproval(), false),
                bool(request.allowOverride(), true), request.createdBy());
    }

    public Optional<PromotionRuleResponse> find(UUID projectId, UUID environmentId) {
        return findOne("""
                select id, project_id, target_environment_id, required_source_environment_id,
                    requires_successful_source_deployment, requires_approval, allow_override, created_by, created_at, updated_at
                from environment_promotion_rules where project_id = ? and target_environment_id = ?
                """, projectId, environmentId);
    }

    public List<PromotionRuleResponse> list(UUID projectId) {
        return jdbcTemplate.query("""
                select id, project_id, target_environment_id, required_source_environment_id,
                    requires_successful_source_deployment, requires_approval, allow_override, created_by, created_at, updated_at
                from environment_promotion_rules where project_id = ? order by created_at, id
                """, this::map, projectId);
    }

    private Optional<PromotionRuleResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::map, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private PromotionRuleResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new PromotionRuleResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("target_environment_id", UUID.class), rs.getObject("required_source_environment_id", UUID.class),
                rs.getBoolean("requires_successful_source_deployment"), rs.getBoolean("requires_approval"),
                rs.getBoolean("allow_override"), rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private boolean bool(Boolean value, boolean fallback) {
        return value == null ? fallback : value;
    }
}
