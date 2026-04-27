package com.deployforge.api.lock;

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
public class DeploymentLockRepository {
    private final JdbcTemplate jdbcTemplate;

    public DeploymentLockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeploymentLockResponse create(UUID projectId, UUID serviceId, UUID environmentId, UUID planId, AcquireDeploymentLockRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into deployment_locks (
                    id, project_id, service_id, environment_id, deployment_plan_id, status, lock_owner,
                    reason, fencing_token, expires_at
                )
                values (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, nextval('deployment_lock_fencing_token_seq'), now() + (? * interval '1 second'))
                returning id, project_id, service_id, environment_id, deployment_plan_id, status, lock_owner, reason,
                    fencing_token, acquired_at, expires_at, released_at, released_by, release_reason
                """, this::map, id, projectId, serviceId, environmentId, planId, request.lockOwner(), request.reason(), request.ttlSeconds());
    }

    public Optional<DeploymentLockResponse> findActiveForServiceEnvironment(UUID serviceId, UUID environmentId) {
        return findOne("""
                select id, project_id, service_id, environment_id, deployment_plan_id, status, lock_owner, reason,
                    fencing_token, acquired_at, expires_at, released_at, released_by, release_reason
                from deployment_locks where service_id = ? and environment_id = ? and status = 'ACTIVE'
                """, serviceId, environmentId);
    }

    public Optional<DeploymentLockResponse> find(UUID projectId, UUID lockId) {
        return findOne("""
                select id, project_id, service_id, environment_id, deployment_plan_id, status, lock_owner, reason,
                    fencing_token, acquired_at, expires_at, released_at, released_by, release_reason
                from deployment_locks where project_id = ? and id = ?
                """, projectId, lockId);
    }

    public List<DeploymentLockResponse> list(UUID projectId) {
        return jdbcTemplate.query("""
                select id, project_id, service_id, environment_id, deployment_plan_id, status, lock_owner, reason,
                    fencing_token, acquired_at, expires_at, released_at, released_by, release_reason
                from deployment_locks where project_id = ? order by acquired_at, id
                """, this::map, projectId);
    }

    public DeploymentLockResponse mark(UUID lockId, DeploymentLockStatus status, String actor, String reason) {
        return jdbcTemplate.queryForObject("""
                update deployment_locks
                set status = ?, released_at = case when ? = 'RELEASED' then now() else released_at end,
                    released_by = case when ? = 'RELEASED' then ? else released_by end,
                    release_reason = case when ? = 'RELEASED' then ? else release_reason end
                where id = ?
                returning id, project_id, service_id, environment_id, deployment_plan_id, status, lock_owner, reason,
                    fencing_token, acquired_at, expires_at, released_at, released_by, release_reason
                """, this::map, status.name(), status.name(), status.name(), actor, status.name(), reason, lockId);
    }

    public boolean activeForPlan(UUID projectId, UUID planId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(select 1 from deployment_locks where project_id = ? and deployment_plan_id = ? and status = 'ACTIVE')
                """, Boolean.class, projectId, planId);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<DeploymentLockResponse> findActiveByPlan(UUID projectId, UUID planId) {
        return findOne("""
                select id, project_id, service_id, environment_id, deployment_plan_id, status, lock_owner, reason,
                    fencing_token, acquired_at, expires_at, released_at, released_by, release_reason
                from deployment_locks where project_id = ? and deployment_plan_id = ? and status = 'ACTIVE'
                """, projectId, planId);
    }

    private Optional<DeploymentLockResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::map, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private DeploymentLockResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new DeploymentLockResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("service_id", UUID.class), rs.getObject("environment_id", UUID.class),
                rs.getObject("deployment_plan_id", UUID.class), DeploymentLockStatus.valueOf(rs.getString("status")),
                rs.getString("lock_owner"), rs.getString("reason"), rs.getLong("fencing_token"),
                rs.getObject("acquired_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("released_at", OffsetDateTime.class), rs.getString("released_by"), rs.getString("release_reason"));
    }
}
