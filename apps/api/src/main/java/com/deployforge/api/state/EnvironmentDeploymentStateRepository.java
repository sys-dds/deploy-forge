package com.deployforge.api.state;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EnvironmentDeploymentStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public EnvironmentDeploymentStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void markPlanned(UUID projectId, UUID serviceId, UUID environmentId, UUID planId) {
        jdbcTemplate.update("""
                insert into environment_deployment_states (
                    id, project_id, service_id, environment_id, current_artifact_id,
                    last_deployment_plan_id, state_status
                )
                values (?, ?, ?, ?, null, ?, 'PLANNED')
                on conflict (service_id, environment_id)
                do update set last_deployment_plan_id = excluded.last_deployment_plan_id,
                    state_status = 'PLANNED',
                    updated_at = now()
                """, UUID.randomUUID(), projectId, serviceId, environmentId, planId);
    }

    public void markDeployed(UUID projectId, UUID serviceId, UUID environmentId, UUID artifactId, UUID planId) {
        jdbcTemplate.update("""
                insert into environment_deployment_states (
                    id, project_id, service_id, environment_id, current_artifact_id,
                    last_deployment_plan_id, state_status
                )
                values (?, ?, ?, ?, ?, ?, 'DEPLOYED')
                on conflict (service_id, environment_id)
                do update set current_artifact_id = excluded.current_artifact_id,
                    last_deployment_plan_id = excluded.last_deployment_plan_id,
                    state_status = 'DEPLOYED',
                    updated_at = now()
                """, UUID.randomUUID(), projectId, serviceId, environmentId, artifactId, planId);
    }

    public Optional<EnvironmentDeploymentStateResponse> find(UUID serviceId, UUID environmentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select project_id, service_id, environment_id, current_artifact_id,
                        last_deployment_plan_id, state_status, updated_at
                    from environment_deployment_states
                    where service_id = ? and environment_id = ?
                    """, this::mapState, serviceId, environmentId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private EnvironmentDeploymentStateResponse mapState(ResultSet rs, int rowNum) throws SQLException {
        return new EnvironmentDeploymentStateResponse(
                rs.getObject("project_id", UUID.class),
                rs.getObject("service_id", UUID.class),
                rs.getObject("environment_id", UUID.class),
                rs.getObject("current_artifact_id", UUID.class),
                rs.getObject("last_deployment_plan_id", UUID.class),
                rs.getString("state_status"),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
