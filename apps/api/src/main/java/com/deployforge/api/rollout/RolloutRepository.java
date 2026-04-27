package com.deployforge.api.rollout;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.plan.DeploymentPlanResponse;
import com.deployforge.api.plan.DeploymentStrategy;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RolloutRepository {
    private final JdbcTemplate jdbcTemplate;

    public RolloutRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RolloutExecutionResponse create(DeploymentPlanResponse plan, StartRolloutRequest request) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.queryForObject("""
                insert into rollout_executions (
                    id, project_id, deployment_plan_id, service_id, environment_id, artifact_id, strategy,
                    status, started_by, reason, current_wave_number
                )
                values (?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, ?, 1)
                returning *
                """, this::mapRollout, id, plan.projectId(), plan.id(), plan.serviceId(), plan.targetEnvironmentId(),
                plan.artifactId(), plan.strategy().name(), request.startedBy(), request.reason());
    }

    public Optional<RolloutExecutionResponse> findByPlan(UUID projectId, UUID planId) {
        return findOne("select * from rollout_executions where project_id = ? and deployment_plan_id = ?", projectId, planId);
    }

    public Optional<RolloutExecutionResponse> find(UUID projectId, UUID rolloutId) {
        return findOne("select * from rollout_executions where project_id = ? and id = ?", projectId, rolloutId);
    }

    public boolean hasActiveForServiceEnvironment(UUID projectId, UUID serviceId, UUID environmentId) {
        Boolean exists = jdbcTemplate.queryForObject("""
                select exists(
                    select 1 from rollout_executions
                    where project_id = ? and service_id = ? and environment_id = ?
                      and status in ('RUNNING', 'WAITING_FOR_GATES', 'PAUSED')
                )
                """, Boolean.class, projectId, serviceId, environmentId);
        return Boolean.TRUE.equals(exists);
    }

    public RolloutExecutionResponse mark(UUID rolloutId, RolloutStatus status) {
        return jdbcTemplate.queryForObject("""
                update rollout_executions
                set status = ?, updated_at = now(),
                    completed_at = case when ? in ('SUCCEEDED','FAILED','ABORTED','ROLLBACK_RECOMMENDED') then now() else completed_at end
                where id = ?
                returning *
                """, this::mapRollout, status.name(), status.name(), rolloutId);
    }

    public RolloutExecutionResponse setCurrentWave(UUID rolloutId, int waveNumber) {
        return jdbcTemplate.queryForObject("""
                update rollout_executions set current_wave_number = ?, status = 'RUNNING', updated_at = now()
                where id = ? returning *
                """, this::mapRollout, waveNumber, rolloutId);
    }

    public RolloutExecutionResponse pause(UUID rolloutId, RolloutActionRequest request) {
        return jdbcTemplate.queryForObject("""
                update rollout_executions
                set status = 'PAUSED', paused_at = now(), paused_by = ?, pause_reason = ?, updated_at = now()
                where id = ? returning *
                """, this::mapRollout, request.actor(), request.reason(), rolloutId);
    }

    public RolloutExecutionResponse resume(UUID rolloutId, RolloutActionRequest request) {
        return jdbcTemplate.queryForObject("""
                update rollout_executions
                set status = 'RUNNING', resumed_at = now(), resumed_by = ?, resume_reason = ?, updated_at = now()
                where id = ? returning *
                """, this::mapRollout, request.actor(), request.reason(), rolloutId);
    }

    public RolloutExecutionResponse abort(UUID rolloutId, RolloutActionRequest request) {
        return jdbcTemplate.queryForObject("""
                update rollout_executions
                set status = 'ABORTED', aborted_at = now(), aborted_by = ?, abort_reason = ?, completed_at = now(), updated_at = now()
                where id = ? returning *
                """, this::mapRollout, request.actor(), request.reason(), rolloutId);
    }

    public RolloutExecutionResponse fail(UUID rolloutId, String reason) {
        return jdbcTemplate.queryForObject("""
                update rollout_executions
                set status = 'FAILED', failure_reason = ?, completed_at = now(), updated_at = now()
                where id = ? returning *
                """, this::mapRollout, reason, rolloutId);
    }

    public void createWaves(RolloutExecutionResponse rollout) {
        int[] percentages = rollout.strategy() == DeploymentStrategy.CANARY ? new int[] {5, 25, 50, 100} : new int[] {100};
        for (int i = 0; i < percentages.length; i++) {
            jdbcTemplate.update("""
                    insert into rollout_waves (
                        id, rollout_execution_id, project_id, deployment_plan_id, wave_number,
                        traffic_percentage, status, started_at
                    )
                    values (?, ?, ?, ?, ?, ?, ?, case when ? = 'RUNNING' then now() else null end)
                    on conflict (rollout_execution_id, wave_number) do nothing
                    """, UUID.randomUUID(), rollout.id(), rollout.projectId(), rollout.deploymentPlanId(), i + 1,
                    percentages[i], i == 0 ? RolloutWaveStatus.RUNNING.name() : RolloutWaveStatus.PENDING.name(),
                    i == 0 ? RolloutWaveStatus.RUNNING.name() : RolloutWaveStatus.PENDING.name());
        }
    }

    public List<RolloutWaveResponse> waves(UUID rolloutId) {
        return jdbcTemplate.query("select * from rollout_waves where rollout_execution_id = ? order by wave_number",
                this::mapWave, rolloutId);
    }

    public Optional<RolloutWaveResponse> wave(UUID rolloutId, int waveNumber) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    select * from rollout_waves where rollout_execution_id = ? and wave_number = ?
                    """, this::mapWave, rolloutId, waveNumber));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public RolloutWaveResponse markWave(UUID waveId, RolloutWaveStatus status, String reason) {
        return jdbcTemplate.queryForObject("""
                update rollout_waves
                set status = ?,
                    started_at = case when ? = 'RUNNING' and started_at is null then now() else started_at end,
                    completed_at = case when ? in ('PASSED','FAILED','ABORTED','SKIPPED') then now() else completed_at end,
                    failure_reason = case when ? = 'FAILED' then ? else failure_reason end,
                    updated_at = now()
                where id = ? returning *
                """, this::mapWave, status.name(), status.name(), status.name(), status.name(), reason, waveId);
    }

    private Optional<RolloutExecutionResponse> findOne(String sql, Object... args) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, this::mapRollout, args));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private RolloutExecutionResponse mapRollout(ResultSet rs, int rowNum) throws SQLException {
        int wave = rs.getInt("current_wave_number");
        return new RolloutExecutionResponse(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class),
                rs.getObject("deployment_plan_id", UUID.class), rs.getObject("service_id", UUID.class),
                rs.getObject("environment_id", UUID.class), rs.getObject("artifact_id", UUID.class),
                DeploymentStrategy.valueOf(rs.getString("strategy")), RolloutStatus.valueOf(rs.getString("status")),
                rs.getString("started_by"), rs.getString("reason"), rs.wasNull() ? null : wave,
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class),
                rs.getObject("paused_at", OffsetDateTime.class), rs.getString("paused_by"), rs.getString("pause_reason"),
                rs.getObject("resumed_at", OffsetDateTime.class), rs.getString("resumed_by"), rs.getString("resume_reason"),
                rs.getObject("aborted_at", OffsetDateTime.class), rs.getString("aborted_by"), rs.getString("abort_reason"),
                rs.getString("failure_reason"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private RolloutWaveResponse mapWave(ResultSet rs, int rowNum) throws SQLException {
        return new RolloutWaveResponse(rs.getObject("id", UUID.class), rs.getObject("rollout_execution_id", UUID.class),
                rs.getObject("project_id", UUID.class), rs.getObject("deployment_plan_id", UUID.class),
                rs.getInt("wave_number"), rs.getInt("traffic_percentage"), RolloutWaveStatus.valueOf(rs.getString("status")),
                rs.getObject("started_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class),
                rs.getString("failure_reason"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }
}
