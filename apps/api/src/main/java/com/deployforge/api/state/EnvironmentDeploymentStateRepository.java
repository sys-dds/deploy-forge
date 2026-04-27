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
        markDeployed(projectId, serviceId, environmentId, artifactId, planId, null);
    }

    public void markDeployed(UUID projectId, UUID serviceId, UUID environmentId, UUID artifactId, UUID planId, UUID rolloutId) {
        jdbcTemplate.update("""
                insert into environment_deployment_states (
                    id, project_id, service_id, environment_id, current_artifact_id,
                    previous_stable_artifact_id, last_deployment_plan_id, last_rollout_execution_id, state_status
                )
                values (?, ?, ?, ?, ?, null, ?, ?, 'DEPLOYED')
                on conflict (service_id, environment_id)
                do update set current_artifact_id = excluded.current_artifact_id,
                    previous_stable_artifact_id = environment_deployment_states.current_artifact_id,
                    last_deployment_plan_id = excluded.last_deployment_plan_id,
                    last_rollout_execution_id = excluded.last_rollout_execution_id,
                    state_status = 'DEPLOYED',
                    updated_at = now()
                """, UUID.randomUUID(), projectId, serviceId, environmentId, artifactId, planId, rolloutId);
    }

    public void markStatus(UUID projectId, UUID serviceId, UUID environmentId, UUID planId, UUID rolloutId, String status) {
        jdbcTemplate.update("""
                insert into environment_deployment_states (
                    id, project_id, service_id, environment_id, current_artifact_id,
                    last_deployment_plan_id, last_rollout_execution_id, state_status
                )
                values (?, ?, ?, ?, null, ?, ?, ?)
                on conflict (service_id, environment_id)
                do update set last_deployment_plan_id = coalesce(excluded.last_deployment_plan_id, environment_deployment_states.last_deployment_plan_id),
                    last_rollout_execution_id = coalesce(excluded.last_rollout_execution_id, environment_deployment_states.last_rollout_execution_id),
                    state_status = excluded.state_status,
                    updated_at = now()
                """, UUID.randomUUID(), projectId, serviceId, environmentId, planId, rolloutId, status);
    }

    public void markRolledBack(UUID projectId, UUID serviceId, UUID environmentId, UUID artifactId, UUID planId,
            UUID rolloutId, UUID rollbackId) {
        jdbcTemplate.update("""
                update environment_deployment_states
                set current_artifact_id = ?,
                    last_deployment_plan_id = ?,
                    last_rollout_execution_id = coalesce(?, last_rollout_execution_id),
                    last_rollback_execution_id = ?,
                    state_status = 'ROLLED_BACK',
                    updated_at = now()
                where project_id = ? and service_id = ? and environment_id = ?
                """, artifactId, planId, rolloutId, rollbackId, projectId, serviceId, environmentId);
    }

    public void markRollbackRunning(UUID projectId, UUID serviceId, UUID environmentId, UUID planId, UUID rolloutId,
            UUID rollbackId) {
        jdbcTemplate.update("""
                update environment_deployment_states
                set last_deployment_plan_id = ?,
                    last_rollout_execution_id = coalesce(?, last_rollout_execution_id),
                    last_rollback_execution_id = ?,
                    state_status = 'ROLLBACK_RUNNING',
                    updated_at = now()
                where project_id = ? and service_id = ? and environment_id = ?
                """, planId, rolloutId, rollbackId, projectId, serviceId, environmentId);
    }

    public void markManualIntervention(UUID projectId, UUID serviceId, UUID environmentId, UUID planId, UUID rolloutId,
            UUID rollbackId) {
        jdbcTemplate.update("""
                insert into environment_deployment_states (
                    id, project_id, service_id, environment_id, last_deployment_plan_id,
                    last_rollout_execution_id, last_rollback_execution_id, state_status
                )
                values (?, ?, ?, ?, ?, ?, ?, 'MANUAL_INTERVENTION_REQUIRED')
                on conflict (service_id, environment_id)
                do update set last_deployment_plan_id = coalesce(excluded.last_deployment_plan_id, environment_deployment_states.last_deployment_plan_id),
                    last_rollout_execution_id = coalesce(excluded.last_rollout_execution_id, environment_deployment_states.last_rollout_execution_id),
                    last_rollback_execution_id = coalesce(excluded.last_rollback_execution_id, environment_deployment_states.last_rollback_execution_id),
                    state_status = 'MANUAL_INTERVENTION_REQUIRED',
                    updated_at = now()
                """, UUID.randomUUID(), projectId, serviceId, environmentId, planId, rolloutId, rollbackId);
    }

    public Optional<EnvironmentDeploymentStateResponse> find(UUID serviceId, UUID environmentId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select project_id, service_id, environment_id, current_artifact_id, previous_stable_artifact_id,
                        last_deployment_plan_id, last_rollout_execution_id, last_rollback_execution_id, state_status, updated_at
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
                rs.getObject("previous_stable_artifact_id", UUID.class),
                rs.getObject("last_deployment_plan_id", UUID.class),
                rs.getObject("last_rollout_execution_id", UUID.class),
                rs.getObject("last_rollback_execution_id", UUID.class),
                rs.getString("state_status"),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
