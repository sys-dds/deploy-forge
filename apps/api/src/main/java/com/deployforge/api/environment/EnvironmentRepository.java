package com.deployforge.api.environment;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
                    id, project_id, name, environment_type, protected_environment, sort_order
                )
                values (?, ?, ?, ?, ?, ?)
                returning id, project_id, name, environment_type, protected_environment, sort_order, created_at
                """, this::mapEnvironment, id, projectId, request.name(), environmentType.name(),
                request.protectedEnvironment(), request.sortOrder());
    }

    public List<EnvironmentResponse> findByProjectId(UUID projectId) {
        return jdbcTemplate.query("""
                select id, project_id, name, environment_type, protected_environment, sort_order, created_at
                from deployment_environments
                where project_id = ?
                order by sort_order, name
                """, this::mapEnvironment, projectId);
    }

    private EnvironmentResponse mapEnvironment(ResultSet rs, int rowNum) throws SQLException {
        return new EnvironmentResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("name"),
                EnvironmentType.valueOf(rs.getString("environment_type")),
                rs.getBoolean("protected_environment"),
                rs.getInt("sort_order"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
