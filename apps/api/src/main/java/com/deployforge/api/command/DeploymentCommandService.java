package com.deployforge.api.command;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentCommandService {
    private final JdbcTemplate jdbcTemplate;
    private final DeploymentIntentEventRepository eventRepository;

    public DeploymentCommandService(JdbcTemplate jdbcTemplate, DeploymentIntentEventRepository eventRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Map<String, Object> create(UUID projectId, String idempotencyKey, JsonNode request) {
        requireProject(projectId);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
        }
        String hash = requestHash(request);
        String commandType = text(request, "commandType");
        validateCommandType(commandType);
        List<Map<String, Object>> existing = jdbcTemplate.query(commandSelect() + " where project_id = ? and idempotency_key = ?",
                CommandRows::command, projectId, idempotencyKey);
        if (!existing.isEmpty()) {
            Map<String, Object> command = existing.get(0);
            if (!hash.equals(command.get("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "COMMAND_IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different request");
            }
            return command;
        }
        UUID id = UUID.randomUUID();
        Map<String, Object> command = jdbcTemplate.queryForObject("""
                insert into deployment_commands (
                    id, project_id, command_type, status, idempotency_key, request_hash, payload_json,
                    priority, max_attempts, created_by, reason
                )
                values (?, ?, ?, 'PENDING', ?, ?, ?, ?, ?, ?, ?)
                returning *
                """, CommandRows::command, id, projectId, commandType, idempotencyKey, hash,
                Jsonb.toPgObject(request.get("payload")), intValue(request, "priority", 100),
                intValue(request, "maxAttempts", 3), text(request, "createdBy"), text(request, "reason"));
        event(projectId, DeploymentIntentEventType.COMMAND_CREATED, text(request, "createdBy"), text(request, "reason"), id);
        return command;
    }

    public Map<String, Object> get(UUID projectId, UUID commandId) {
        return jdbcTemplate.query(commandSelect() + " where project_id = ? and id = ?", CommandRows::command, projectId, commandId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMAND_NOT_FOUND", "Deployment command not found"));
    }

    public List<Map<String, Object>> backlog(UUID projectId) {
        return jdbcTemplate.query("""
                select status, count(*) as count
                from deployment_commands
                where project_id = ?
                group by status
                order by status
                """, (rs, row) -> Map.of("status", rs.getString("status"), "count", rs.getInt("count")), projectId);
    }

    public List<Map<String, Object>> parked(UUID projectId) {
        return jdbcTemplate.query("""
                select p.id, p.command_id, p.parked_by, p.reason, p.parked_at, p.requeued_at, p.requeued_by, p.requeue_reason
                from parked_deployment_commands p
                join deployment_commands c on c.id = p.command_id
                where c.project_id = ?
                order by p.parked_at desc
                """, CommandRows::parked, projectId);
    }

    @Transactional
    public Map<String, Object> requeue(UUID projectId, UUID commandId, JsonNode request) {
        Map<String, Object> command = get(projectId, commandId);
        if (!"PARKED".equals(command.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "COMMAND_NOT_PARKED", "Only parked commands can be requeued");
        }
        jdbcTemplate.update("""
                update deployment_commands
                set status = 'PENDING', next_attempt_at = now(), leased_by_node_id = null, lease_expires_at = null,
                    parked_reason = null, updated_at = now()
                where id = ?
                """, commandId);
        jdbcTemplate.update("""
                update parked_deployment_commands
                set requeued_at = now(), requeued_by = ?, requeue_reason = ?
                where command_id = ? and requeued_at is null
                """, text(request, "requeuedBy"), text(request, "reason"), commandId);
        event(projectId, DeploymentIntentEventType.COMMAND_REQUEUED, text(request, "requeuedBy"), text(request, "reason"), commandId);
        return get(projectId, commandId);
    }

    @Transactional
    public void markParked(UUID projectId, UUID commandId, String actor, String reason) {
        jdbcTemplate.update("""
                update deployment_commands
                set status = 'PARKED', parked_reason = ?, leased_by_node_id = null, lease_expires_at = null, updated_at = now()
                where id = ?
                """, reason, commandId);
        jdbcTemplate.update("""
                insert into parked_deployment_commands (id, command_id, parked_by, reason)
                values (?, ?, ?, ?)
                """, UUID.randomUUID(), commandId, actor, reason);
        event(projectId, DeploymentIntentEventType.COMMAND_PARKED, actor, reason, commandId);
    }

    @Transactional
    public void markPendingForRetry(UUID commandId, String error, int attempts) {
        int delaySeconds = attempts <= 1 ? 10 : 30;
        jdbcTemplate.update("""
                update deployment_commands
                set status = 'PENDING', next_attempt_at = now() + (? * interval '1 second'), leased_by_node_id = null,
                    lease_expires_at = null, last_error = ?, updated_at = now()
                where id = ?
                """, delaySeconds, error, commandId);
    }

    public String requestHash(JsonNode request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Jsonb.MAPPER.writeValueAsBytes(request);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to hash command request", exception);
        }
    }

    public String hashText(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Unable to hash value", exception);
        }
    }

    private void requireProject(UUID projectId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from deployment_projects where id = ?", Integer.class, projectId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
        }
    }

    private void validateCommandType(String commandType) {
        if (!List.of("ROLLOUT_START", "ROLLOUT_ADVANCE", "ROLLOUT_PAUSE", "ROLLOUT_RESUME", "ROLLOUT_ABORT",
                "ROLLBACK_START", "ROLLBACK_COMPLETE_SUCCESS", "ROLLBACK_COMPLETE_FAILURE", "ROLLBACK_RETRY",
                "DRIFT_CHECK", "RECONCILE_ENVIRONMENT", "CREATE_REPAIR_INTENT", "VERIFY_CONSISTENCY").contains(commandType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_COMMAND_TYPE", "Unsupported command type: " + commandType);
        }
    }

    private void event(UUID projectId, DeploymentIntentEventType type, String actor, String reason, UUID commandId) {
        eventRepository.record(projectId, null, null, null, null, type, actor, reason,
                Jsonb.object().put("commandId", commandId.toString()));
    }

    private static String commandSelect() {
        return """
                select id, project_id, command_type, status, idempotency_key, request_hash, payload_json::text,
                    priority, attempts, max_attempts, next_attempt_at, leased_by_node_id, lease_expires_at,
                    fencing_token, last_error, parked_reason, created_by, reason, created_at, updated_at, completed_at
                from deployment_commands
                """;
    }

    public static ObjectNode createCommandBody(String type, JsonNode payload, String actor, String reason) {
        ObjectNode body = Jsonb.object();
        body.put("commandType", type);
        body.put("priority", 100);
        body.put("maxAttempts", 3);
        body.put("createdBy", actor);
        body.put("reason", reason);
        body.set("payload", Jsonb.emptyObjectIfNull(payload));
        return body;
    }

    private static String text(JsonNode node, String field) {
        String value = node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_FIELD_MISSING", field + " is required");
        }
        return value;
    }

    private static int intValue(JsonNode node, String field, int fallback) {
        return node != null && node.hasNonNull(field) ? node.get(field).asInt() : fallback;
    }
}
