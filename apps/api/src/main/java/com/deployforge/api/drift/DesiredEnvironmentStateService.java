package com.deployforge.api.drift;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DesiredEnvironmentStateService {
    private final JdbcTemplate jdbcTemplate;
    private final DeploymentIntentEventRepository eventRepository;

    public DesiredEnvironmentStateService(JdbcTemplate jdbcTemplate, DeploymentIntentEventRepository eventRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventRepository = eventRepository;
    }

    public void recordRolloutSuccess(UUID projectId, UUID serviceId, UUID environmentId, UUID artifactId, UUID planId,
            UUID rolloutId, String actor, String reason) {
        upsert(projectId, serviceId, environmentId, artifactId, null, "ROLLOUT_SUCCESS", actor, reason, rolloutId, null);
        eventRepository.record(projectId, planId, serviceId, environmentId, artifactId, DeploymentIntentEventType.DESIRED_STATE_RECORDED,
                actor, reason, Jsonb.object().put("desiredSource", "ROLLOUT_SUCCESS"));
    }

    public void recordRollbackSuccess(UUID projectId, UUID serviceId, UUID environmentId, UUID artifactId, UUID planId,
            UUID rolloutId, UUID rollbackId, String actor, String reason) {
        upsert(projectId, serviceId, environmentId, artifactId, null, "ROLLBACK_SUCCESS", actor, reason, rolloutId, rollbackId);
        eventRepository.record(projectId, planId, serviceId, environmentId, artifactId, DeploymentIntentEventType.DESIRED_STATE_RECORDED,
                actor, reason, Jsonb.object().put("desiredSource", "ROLLBACK_SUCCESS"));
    }

    public void acceptActual(UUID projectId, UUID serviceId, UUID environmentId, UUID artifactId, String digest,
            String actor, String reason) {
        upsert(projectId, serviceId, environmentId, artifactId, digest, "OPERATOR_ACCEPTED_ACTUAL", actor, reason, null, null);
    }

    public Map<String, Object> get(UUID projectId, UUID serviceId, UUID environmentId) {
        return jdbcTemplate.query("""
                select d.*, a.image_digest
                from desired_environment_states d
                left join release_artifacts a on a.id = d.desired_artifact_id
                where d.project_id = ? and d.service_id = ? and d.environment_id = ?
                """, DriftRows::mapDesired, projectId, serviceId, environmentId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DESIRED_STATE_NOT_FOUND", "Desired state not found"));
    }

    public Optional<Map<String, Object>> find(UUID projectId, UUID serviceId, UUID environmentId) {
        return jdbcTemplate.query("""
                select d.*, a.image_digest
                from desired_environment_states d
                left join release_artifacts a on a.id = d.desired_artifact_id
                where d.project_id = ? and d.service_id = ? and d.environment_id = ?
                """, DriftRows::mapDesired, projectId, serviceId, environmentId).stream().findFirst();
    }

    private void upsert(UUID projectId, UUID serviceId, UUID environmentId, UUID artifactId, String explicitDigest,
            String source, String actor, String reason, UUID rolloutId, UUID rollbackId) {
        String digest = explicitDigest;
        if (artifactId != null) {
            Map<String, Object> artifact = jdbcTemplate.queryForMap(
                    "select project_id, service_id, image_digest from release_artifacts where id = ?", artifactId);
            if (!projectId.equals(artifact.get("project_id")) || !serviceId.equals(artifact.get("service_id"))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DESIRED_ARTIFACT_SERVICE_MISMATCH",
                        "Desired artifact must belong to the same project and service");
            }
            digest = (String) artifact.get("image_digest");
        }
        jdbcTemplate.update("""
                insert into desired_environment_states (
                    id, project_id, service_id, environment_id, desired_artifact_id, desired_image_digest,
                    desired_source, desired_reason, recorded_by, rollout_execution_id, rollback_execution_id
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (service_id, environment_id)
                do update set desired_artifact_id = excluded.desired_artifact_id,
                    desired_image_digest = excluded.desired_image_digest,
                    desired_source = excluded.desired_source,
                    desired_reason = excluded.desired_reason,
                    recorded_by = excluded.recorded_by,
                    rollout_execution_id = excluded.rollout_execution_id,
                    rollback_execution_id = excluded.rollback_execution_id,
                    updated_at = now()
                """, UUID.randomUUID(), projectId, serviceId, environmentId, artifactId, digest, source,
                reason, actor, rolloutId, rollbackId);
    }
}
