package com.deployforge.api.event;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeploymentIntentEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public DeploymentIntentEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(UUID projectId, UUID planId, UUID serviceId, UUID environmentId, UUID artifactId,
            DeploymentIntentEventType eventType, String actor, String reason, JsonNode metadata) {
        jdbcTemplate.update("""
                insert into deployment_intent_events (
                    id, project_id, deployment_plan_id, service_id, environment_id, artifact_id,
                    event_type, actor, reason, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, planId, serviceId, environmentId, artifactId,
                eventType.name(), actor == null || actor.isBlank() ? "system" : actor,
                reason, Jsonb.toPgObject(metadata));
    }

    public List<DeploymentIntentEventResponse> find(UUID projectId, UUID planId, DeploymentIntentEventType eventType) {
        String sql = """
                select id, project_id, deployment_plan_id, service_id, environment_id, artifact_id,
                    event_type, actor, reason, metadata_json::text, created_at
                from deployment_intent_events
                where project_id = ?
                  and (?::uuid is null or deployment_plan_id = ?::uuid)
                  and (?::text is null or event_type = ?::text)
                order by created_at, id
                """;
        String eventName = eventType == null ? null : eventType.name();
        return jdbcTemplate.query(sql, this::mapEvent, projectId, planId, planId, eventName, eventName);
    }

    private DeploymentIntentEventResponse mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new DeploymentIntentEventResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("deployment_plan_id", UUID.class),
                rs.getObject("service_id", UUID.class),
                rs.getObject("environment_id", UUID.class),
                rs.getObject("artifact_id", UUID.class),
                DeploymentIntentEventType.valueOf(rs.getString("event_type")),
                rs.getString("actor"),
                rs.getString("reason"),
                Jsonb.fromString(rs.getString("metadata_json")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }
}
