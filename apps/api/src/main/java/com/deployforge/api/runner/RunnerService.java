package com.deployforge.api.runner;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.command.CommandExecutionService;
import com.deployforge.api.command.CommandRows;
import com.deployforge.api.command.DeploymentCommandService;
import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunnerService {
    private final JdbcTemplate jdbcTemplate;
    private final DeploymentIntentEventRepository eventRepository;
    private final DeploymentCommandService commandService;
    private final CommandExecutionService executionService;

    public RunnerService(JdbcTemplate jdbcTemplate, DeploymentIntentEventRepository eventRepository,
            DeploymentCommandService commandService, CommandExecutionService executionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventRepository = eventRepository;
        this.commandService = commandService;
        this.executionService = executionService;
    }

    @Transactional
    public Map<String, Object> register(UUID projectId, JsonNode request) {
        String nodeId = text(request, "nodeId");
        Map<String, Object> runner = jdbcTemplate.query("""
                insert into runner_nodes (id, node_id, status, metadata_json)
                values (?, ?, 'ACTIVE', ?)
                on conflict (node_id) do update
                set status = 'ACTIVE', last_seen_at = now(), metadata_json = excluded.metadata_json
                returning id, node_id, status, started_at, last_seen_at, metadata_json::text
                """, RunnerRows::runner, UUID.randomUUID(), nodeId, Jsonb.toPgObject(request.get("metadata"))).get(0);
        event(projectId, DeploymentIntentEventType.RUNNER_REGISTERED, nodeId, "Runner registered", null);
        return runner;
    }

    @Transactional
    public Map<String, Object> heartbeat(UUID projectId, String nodeId) {
        Map<String, Object> runner = jdbcTemplate.query("""
                update runner_nodes
                set status = 'ACTIVE', last_seen_at = now()
                where node_id = ?
                returning id, node_id, status, started_at, last_seen_at, metadata_json::text
                """, RunnerRows::runner, nodeId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUNNER_NOT_FOUND", "Runner node not found"));
        event(projectId, DeploymentIntentEventType.RUNNER_HEARTBEAT_RECORDED, nodeId, "Runner heartbeat recorded", null);
        return runner;
    }

    @Transactional
    public List<Map<String, Object>> runners(UUID projectId) {
        List<String> staleNodes = jdbcTemplate.queryForList("""
                select node_id
                from runner_nodes
                where status = 'ACTIVE' and last_seen_at < now() - interval '300 seconds'
                """, String.class);
        jdbcTemplate.update("""
                update runner_nodes
                set status = 'STALE'
                where status = 'ACTIVE' and last_seen_at < now() - interval '300 seconds'
                """);
        staleNodes.forEach(nodeId -> event(projectId, DeploymentIntentEventType.RUNNER_MARKED_STALE,
                nodeId, "Runner marked stale", null));
        return jdbcTemplate.query("""
                select id, node_id, status, started_at, last_seen_at, metadata_json::text
                from runner_nodes
                order by node_id
                """, RunnerRows::runner);
    }

    @Transactional
    public Map<String, Object> claim(UUID projectId, String nodeId, JsonNode request) {
        heartbeat(projectId, nodeId);
        int leaseSeconds = request != null && request.hasNonNull("leaseSeconds") ? request.get("leaseSeconds").asInt() : 60;
        List<String> supported = supportedTypes(request);
        String typeFilter = supported.isEmpty() ? "" : " and command_type = any (?) ";
        Object[] args = supported.isEmpty() ? new Object[] {projectId} : new Object[] {projectId, supported.toArray(String[]::new)};
        List<Map<String, Object>> commands = jdbcTemplate.query("""
                select id, project_id, command_type, status, idempotency_key, request_hash, payload_json::text,
                    priority, attempts, max_attempts, next_attempt_at, leased_by_node_id, lease_expires_at,
                    fencing_token, last_error, parked_reason, created_by, reason, created_at, updated_at, completed_at
                from deployment_commands
                where project_id = ?
                  and (status = 'PENDING' or (status in ('LEASED','RUNNING') and lease_expires_at < now()))
                  and next_attempt_at <= now()
                """ + typeFilter + """
                order by priority asc, created_at asc
                limit 1
                for update skip locked
                """, CommandRows::command, args);
        if (commands.isEmpty()) {
            return Map.of("status", "NO_COMMAND");
        }
        Map<String, Object> existing = commands.get(0);
        UUID commandId = (UUID) existing.get("commandId");
        long token = ((Number) existing.get("fencingToken")).longValue() + 1;
        int attempt = ((Number) existing.get("attempts")).intValue() + 1;
        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(leaseSeconds);
        Map<String, Object> command = jdbcTemplate.query("""
                update deployment_commands
                set status = 'RUNNING', attempts = attempts + 1, leased_by_node_id = ?, lease_expires_at = ?,
                    fencing_token = fencing_token + 1, updated_at = now()
                where id = ?
                returning id, project_id, command_type, status, idempotency_key, request_hash, payload_json::text,
                    priority, attempts, max_attempts, next_attempt_at, leased_by_node_id, lease_expires_at,
                    fencing_token, last_error, parked_reason, created_by, reason, created_at, updated_at, completed_at
                """, CommandRows::command, nodeId, Timestamp.from(expiresAt.toInstant()), commandId).get(0);
        jdbcTemplate.update("""
                insert into deployment_command_attempts (id, command_id, attempt_number, runner_node_id, fencing_token, status)
                values (?, ?, ?, ?, ?, 'RUNNING')
                """, UUID.randomUUID(), commandId, attempt, nodeId, token);
        event(projectId, DeploymentIntentEventType.COMMAND_LEASED, nodeId, "Command leased", commandId);
        event(projectId, DeploymentIntentEventType.COMMAND_STARTED, nodeId, "Command started", commandId);
        return command;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> completeSuccess(UUID projectId, String nodeId, UUID commandId, JsonNode request) {
        Map<String, Object> command = requireCompletion(projectId, nodeId, commandId, request);
        jdbcTemplate.update("""
                update deployment_commands
                set status = 'SUCCEEDED', completed_at = now(), leased_by_node_id = null, lease_expires_at = null, updated_at = now()
                where id = ?
                """, commandId);
        jdbcTemplate.update("""
                update deployment_command_attempts
                set status = 'SUCCEEDED', completed_at = now(), result_json = ?
                where command_id = ? and fencing_token = ?
                """, Jsonb.toPgObject(request.get("result")), commandId, longValue(request, "fencingToken"));
        event(projectId, DeploymentIntentEventType.COMMAND_SUCCEEDED, nodeId, "Command succeeded", commandId);
        return commandService.get(projectId, commandId);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> completeFailure(UUID projectId, String nodeId, UUID commandId, JsonNode request) {
        Map<String, Object> command = requireCompletion(projectId, nodeId, commandId, request);
        String error = text(request, "errorMessage");
        jdbcTemplate.update("""
                update deployment_command_attempts
                set status = 'FAILED', completed_at = now(), error_message = ?
                where command_id = ? and fencing_token = ?
                """, error, commandId, longValue(request, "fencingToken"));
        int attempts = ((Number) command.get("attempts")).intValue();
        int maxAttempts = ((Number) command.get("maxAttempts")).intValue();
        event(projectId, DeploymentIntentEventType.COMMAND_FAILED, nodeId, error, commandId);
        if (attempts < maxAttempts) {
            commandService.markPendingForRetry(commandId, error, attempts);
        } else {
            commandService.markParked(projectId, commandId, nodeId, error);
        }
        return commandService.get(projectId, commandId);
    }

    @Transactional
    public Map<String, Object> tick(UUID projectId, String nodeId, JsonNode request) {
        Map<String, Object> command = claim(projectId, nodeId, request);
        if ("NO_COMMAND".equals(command.get("status"))) {
            return command;
        }
        try {
            JsonNode result = executionService.execute(projectId, command);
            Map<String, Object> completed = completeSuccess(projectId, nodeId, (UUID) command.get("commandId"),
                    Jsonb.object().put("fencingToken", ((Number) command.get("fencingToken")).longValue()).set("result", result));
            return Map.of("status", "EXECUTED", "command", completed, "result", result);
        } catch (RuntimeException exception) {
            Map<String, Object> failed = completeFailure(projectId, nodeId, (UUID) command.get("commandId"),
                    Jsonb.object().put("fencingToken", ((Number) command.get("fencingToken")).longValue())
                            .put("errorMessage", exception.getMessage()));
            return Map.of("status", "FAILED", "command", failed, "error", exception.getMessage());
        }
    }

    private Map<String, Object> requireCompletion(UUID projectId, String nodeId, UUID commandId, JsonNode request) {
        Map<String, Object> command = commandService.get(projectId, commandId);
        long provided = longValue(request, "fencingToken");
        long current = ((Number) command.get("fencingToken")).longValue();
        if (provided != current || !"RUNNING".equals(command.get("status")) || !nodeId.equals(command.get("leasedByNodeId"))) {
            jdbcTemplate.update("""
                    update deployment_command_attempts
                    set status = 'STALE_REJECTED', completed_at = now(), error_message = 'Stale fencing token rejected'
                    where command_id = ? and fencing_token = ?
                    """, commandId, provided);
            event(projectId, DeploymentIntentEventType.COMMAND_STALE_COMPLETION_REJECTED, nodeId, "Stale completion rejected", commandId);
            throw new ApiException(HttpStatus.CONFLICT, "COMMAND_FENCING_TOKEN_STALE", "Command completion used a stale fencing token");
        }
        return command;
    }

    private List<String> supportedTypes(JsonNode request) {
        if (request == null || !request.has("supportedCommandTypes") || !request.get("supportedCommandTypes").isArray()) {
            return List.of();
        }
        ArrayNode array = (ArrayNode) request.get("supportedCommandTypes");
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asText()));
        return values;
    }

    private void event(UUID projectId, DeploymentIntentEventType type, String actor, String reason, UUID commandId) {
        eventRepository.record(projectId, null, null, null, null, type, actor, reason,
                commandId == null ? Jsonb.object() : Jsonb.object().put("commandId", commandId.toString()));
    }

    private static String text(JsonNode node, String field) {
        String value = node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_FIELD_MISSING", field + " is required");
        }
        return value;
    }

    private static long longValue(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_FIELD_MISSING", field + " is required");
        }
        return node.get(field).asLong();
    }
}
