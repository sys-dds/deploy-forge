package com.deployforge.api.ops;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.command.CommandRows;
import com.deployforge.api.command.DeploymentCommandService;
import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorRecoveryService {
    private final JdbcTemplate jdbcTemplate;
    private final DeploymentCommandService commandService;
    private final DeploymentIntentEventRepository eventRepository;

    public OperatorRecoveryService(JdbcTemplate jdbcTemplate, DeploymentCommandService commandService,
            DeploymentIntentEventRepository eventRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.commandService = commandService;
        this.eventRepository = eventRepository;
    }

    public List<Map<String, Object>> stuckCommands(UUID projectId) {
        return jdbcTemplate.query(commandSelect() + """
                where project_id = ? and status in ('LEASED','RUNNING') and lease_expires_at < now()
                order by lease_expires_at asc
                """, CommandRows::command, projectId);
    }

    public List<Map<String, Object>> staleLeases(UUID projectId) {
        return stuckCommands(projectId);
    }

    public List<Map<String, Object>> stuckRollouts(UUID projectId) {
        return jdbcTemplate.queryForList("""
                select id as "rolloutId", deployment_plan_id as "deploymentPlanId", service_id as "serviceId",
                    environment_id as "environmentId", status, started_at as "startedAt"
                from rollout_executions
                where project_id = ? and status in ('RUNNING','WAITING_FOR_GATES','PAUSED')
                order by started_at asc
                """, projectId);
    }

    public List<Map<String, Object>> stuckRollbacks(UUID projectId) {
        return jdbcTemplate.queryForList("""
                select id as "rollbackExecutionId", rollback_recommendation_id as "rollbackRecommendationId",
                    service_id as "serviceId", environment_id as "environmentId", status, started_at as "startedAt"
                from rollback_executions
                where project_id = ? and status in ('RUNNING','WAITING_FOR_GATES')
                order by started_at asc
                """, projectId);
    }

    public Map<String, Object> summary(UUID projectId) {
        return Map.of(
                "commands", jdbcTemplate.queryForList("""
                        select status, count(*) as count
                        from deployment_commands
                        where project_id = ?
                        group by status
                        """, projectId),
                "openDriftCount", count("select count(*) from drift_findings where project_id = ? and status in ('OPEN','ACKNOWLEDGED')", projectId),
                "proposedRepairPlanCount", count("select count(*) from repair_plans where project_id = ? and status = 'PROPOSED'", projectId),
                "stuckCommandCount", stuckCommands(projectId).size(),
                "activeRolloutCount", count("select count(*) from rollout_executions where project_id = ? and status in ('RUNNING','WAITING_FOR_GATES','PAUSED')", projectId),
                "activeRollbackCount", count("select count(*) from rollback_executions where project_id = ? and status in ('RUNNING','WAITING_FOR_GATES')", projectId));
    }

    public Map<String, Object> investigate(UUID projectId) {
        return Map.of(
                "commands", jdbcTemplate.query(commandSelect() + " where project_id = ? order by created_at desc limit 25",
                        CommandRows::command, projectId),
                "driftFindings", jdbcTemplate.queryForList("""
                        select id as "findingId", drift_type as "driftType", severity, status, message, recommended_action as "recommendedAction"
                        from drift_findings
                        where project_id = ?
                        order by first_detected_at desc
                        limit 25
                        """, projectId),
                "repairPlans", jdbcTemplate.queryForList("""
                        select id as "repairPlanId", plan_type as "planType", status, requires_approval as "requiresApproval", created_at as "createdAt"
                        from repair_plans
                        where project_id = ?
                        order by created_at desc
                        limit 25
                        """, projectId));
    }

    @Transactional
    public Map<String, Object> forcePark(UUID projectId, UUID commandId, JsonNode request) {
        requireRisk(request);
        commandService.markParked(projectId, commandId, text(request, "actor"), text(request, "reason"));
        record(projectId, "FORCE_PARK_COMMAND", "COMMAND", commandId, request);
        return commandService.get(projectId, commandId);
    }

    @Transactional
    public Map<String, Object> forceRetry(UUID projectId, UUID commandId, JsonNode request) {
        requireRisk(request);
        commandService.get(projectId, commandId);
        jdbcTemplate.update("""
                update deployment_commands
                set status = 'PENDING', next_attempt_at = now(), leased_by_node_id = null, lease_expires_at = null,
                    parked_reason = null, updated_at = now()
                where project_id = ? and id = ?
                """, projectId, commandId);
        record(projectId, "FORCE_RETRY_COMMAND", "COMMAND", commandId, request);
        return commandService.get(projectId, commandId);
    }

    @Transactional
    public Map<String, Object> manualResolve(UUID projectId, UUID commandId, JsonNode request) {
        requireRisk(request);
        commandService.get(projectId, commandId);
        jdbcTemplate.update("""
                update deployment_commands
                set status = 'SUCCEEDED', completed_at = now(), leased_by_node_id = null, lease_expires_at = null,
                    updated_at = now()
                where project_id = ? and id = ?
                """, projectId, commandId);
        record(projectId, "MARK_COMMAND_MANUALLY_RESOLVED", "COMMAND", commandId, request);
        return commandService.get(projectId, commandId);
    }

    @Transactional
    public Map<String, Object> forceReleaseStaleLease(UUID projectId, UUID commandId, JsonNode request) {
        requireRisk(request);
        Map<String, Object> command = commandService.get(projectId, commandId);
        if (command.get("leaseExpiresAt") == null || !stuckCommands(projectId).stream()
                .anyMatch(row -> commandId.equals(row.get("commandId")))) {
            throw new ApiException(HttpStatus.CONFLICT, "LEASE_NOT_STALE", "Only stale or expired leases can be force released");
        }
        jdbcTemplate.update("""
                update deployment_commands
                set status = 'PENDING', leased_by_node_id = null, lease_expires_at = null, updated_at = now()
                where project_id = ? and id = ?
                """, projectId, commandId);
        record(projectId, "FORCE_RELEASE_STALE_LEASE", "COMMAND", commandId, request);
        return commandService.get(projectId, commandId);
    }

    public List<Map<String, Object>> recoveryEvents(UUID projectId) {
        return jdbcTemplate.queryForList("""
                select id as "operatorRecoveryActionId", action_type as "actionType", target_type as "targetType",
                    target_id as "targetId", actor, reason, risk_acknowledgement as "riskAcknowledgement",
                    metadata_json as "metadata", created_at as "createdAt"
                from operator_recovery_actions
                where project_id = ?
                order by created_at desc
                """, projectId);
    }

    private void record(UUID projectId, String actionType, String targetType, UUID targetId, JsonNode request) {
        jdbcTemplate.update("""
                insert into operator_recovery_actions (
                    id, project_id, action_type, target_type, target_id, actor, reason, risk_acknowledgement, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), projectId, actionType, targetType, targetId, text(request, "actor"),
                text(request, "reason"), text(request, "riskAcknowledgement"), Jsonb.toPgObject(request.get("metadata")));
        eventRepository.record(projectId, null, null, null, null, DeploymentIntentEventType.OPERATOR_RECOVERY_ACTION_RECORDED,
                text(request, "actor"), text(request, "reason"), Jsonb.object().put("actionType", actionType)
                        .put("targetType", targetType).put("targetId", targetId.toString()));
    }

    private void requireRisk(JsonNode request) {
        text(request, "actor");
        text(request, "reason");
        text(request, "riskAcknowledgement");
    }

    private Integer count(String sql, UUID projectId) {
        return jdbcTemplate.queryForObject(sql, Integer.class, projectId);
    }

    private static String commandSelect() {
        return """
                select id, project_id, command_type, status, idempotency_key, request_hash, payload_json::text,
                    priority, attempts, max_attempts, next_attempt_at, leased_by_node_id, lease_expires_at,
                    fencing_token, last_error, parked_reason, created_by, reason, created_at, updated_at, completed_at
                from deployment_commands
                """;
    }

    private static String text(JsonNode node, String field) {
        String value = node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_FIELD_MISSING", field + " is required");
        }
        return value;
    }
}
