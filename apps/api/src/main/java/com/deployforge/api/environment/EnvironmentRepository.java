package com.deployforge.api.environment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.project.LifecycleStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EnvironmentRepository {

    private final JdbcTemplate jdbcTemplate;

    public EnvironmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EnvironmentResponse create(UUID projectId, EnvironmentType environmentType, CreateEnvironmentRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_environments (
                    id, project_id, name, environment_type, protected_environment, sort_order,
                    external_target_id, requires_approval, lifecycle_status
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, name, environment_type, protected_environment, sort_order,
                    external_target_id, requires_approval, lifecycle_status, created_at
                """, this::mapEnvironment, id, projectId, request.name(), environmentType.name(),
                request.protectedEnvironment(), request.sortOrder(), request.externalTargetId(),
                request.requiresApproval(), lifecycleStatus(request.lifecycleStatus()).name());
    }

    public List<EnvironmentResponse> findByProjectId(UUID projectId) {
        return jdbcTemplate.query("""
                select id, project_id, name, environment_type, protected_environment, sort_order,
                    external_target_id, requires_approval, lifecycle_status, created_at
                from deployment_environments
                where project_id = ?
                order by sort_order, name
                """, this::mapEnvironment, projectId);
    }

    public Optional<EnvironmentResponse> findById(UUID environmentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select id, project_id, name, environment_type, protected_environment, sort_order,
                        external_target_id, requires_approval, lifecycle_status, created_at
                    from deployment_environments
                    where id = ?
                    """, this::mapEnvironment, environmentId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private EnvironmentResponse mapEnvironment(ResultSet rs, int rowNum) throws SQLException {
        return new EnvironmentResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("name"),
                EnvironmentType.valueOf(rs.getString("environment_type")),
                rs.getBoolean("protected_environment"),
                rs.getInt("sort_order"),
                rs.getString("external_target_id"),
                rs.getBoolean("requires_approval"),
                LifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private LifecycleStatus lifecycleStatus(LifecycleStatus lifecycleStatus) {
        return lifecycleStatus == null ? LifecycleStatus.ACTIVE : lifecycleStatus;
    }
}
