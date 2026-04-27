package com.deployforge.api.reconcile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.command.DeploymentCommandService;
import com.deployforge.api.drift.DriftService;
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
public class ReconciliationService {
    private final JdbcTemplate jdbcTemplate;
    private final DriftService driftService;
    private final DeploymentCommandService commandService;
    private final DeploymentIntentEventRepository eventRepository;

    public ReconciliationService(JdbcTemplate jdbcTemplate, DriftService driftService,
            DeploymentCommandService commandService, DeploymentIntentEventRepository eventRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.driftService = driftService;
        this.commandService = commandService;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Map<String, Object> putPolicy(UUID projectId, JsonNode request) {
        UUID serviceId = optionalUuid(request, "serviceId");
        UUID environmentId = optionalUuid(request, "environmentId");
        return jdbcTemplate.query("""
                insert into reconciliation_policies (
                    id, project_id, service_id, environment_id, enabled, auto_create_repair_intents,
                    require_approval_for_repair, max_auto_repair_severity, created_by, reason
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (project_id, service_id, environment_id) do update
                set enabled = excluded.enabled,
                    auto_create_repair_intents = excluded.auto_create_repair_intents,
                    require_approval_for_repair = excluded.require_approval_for_repair,
                    max_auto_repair_severity = excluded.max_auto_repair_severity,
                    updated_at = now()
                returning *
                """, ReconciliationRows::policy, UUID.randomUUID(), projectId, serviceId, environmentId,
                bool(request, "enabled", true), bool(request, "autoCreateRepairIntents", false),
                bool(request, "requireApprovalForRepair", true), text(request, "maxAutoRepairSeverity", "WARNING"),
                text(request, "createdBy"), text(request, "reason")).get(0);
    }

    @Transactional
    public Map<String, Object> run(UUID projectId, String idempotencyKey, JsonNode request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
        }
        String hash = commandService.requestHash(Jsonb.emptyObjectIfNull(request));
        List<Map<String, Object>> existing = jdbcTemplate.query(runSelect() + " where project_id = ? and idempotency_key = ?",
                ReconciliationRows::run, projectId, idempotencyKey);
        if (!existing.isEmpty()) {
            Map<String, Object> run = existing.get(0);
            if (!hash.equals(run.get("requestHash"))) {
                throw new ApiException(HttpStatus.CONFLICT, "RECONCILIATION_IDEMPOTENCY_CONFLICT",
                        "Idempotency key was reused with a different reconciliation request");
            }
            return evidence(projectId, (UUID) run.get("runId"));
        }
        UUID runId = UUID.randomUUID();
        UUID serviceId = optionalUuid(request, "serviceId");
        UUID environmentId = optionalUuid(request, "environmentId");
        Map<String, Object> run = jdbcTemplate.query("""
                insert into reconciliation_runs (
                    id, project_id, service_id, environment_id, status, requested_by, reason, idempotency_key, request_hash
                )
                values (?, ?, ?, ?, 'RUNNING', ?, ?, ?, ?)
                returning id, project_id, service_id, environment_id, status, requested_by, reason, idempotency_key,
                    request_hash, started_at, completed_at, summary_json::text
                """, ReconciliationRows::run, runId, projectId, serviceId, environmentId, text(request, "requestedBy"),
                text(request, "reason"), idempotencyKey, hash).get(0);
        eventRepository.record(projectId, null, serviceId, environmentId, null, DeploymentIntentEventType.RECONCILIATION_RUN_CREATED,
                text(request, "requestedBy"), text(request, "reason"), Jsonb.object().put("reconciliationRunId", runId.toString()));
        List<Map<String, Object>> findings = driftService.findings(projectId, serviceId, environmentId, null, null, null).stream()
                .filter(finding -> !"RESOLVED".equals(finding.get("status")))
                .toList();
        boolean requiresApproval = policyRequiresApproval(projectId, serviceId, environmentId);
        for (Map<String, Object> finding : findings) {
            UUID findingId = (UUID) finding.get("driftFindingId");
            String action = finding.get("recommendedAction").toString();
            String planType = planType(action);
            jdbcTemplate.update("""
                    insert into reconciliation_issues (
                        id, reconciliation_run_id, project_id, drift_finding_id, issue_type, severity, message,
                        recommended_action, metadata_json
                    )
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), runId, projectId, findingId, finding.get("driftType"), finding.get("severity"),
                    finding.get("message"), action, Jsonb.toPgObject(Jsonb.MAPPER.valueToTree(finding)));
            UUID planId = UUID.randomUUID();
            jdbcTemplate.update("""
                    insert into repair_plans (
                        id, project_id, reconciliation_run_id, drift_finding_id, plan_type, status, requires_approval,
                        evidence_snapshot_json, requested_by, reason
                    )
                    values (?, ?, ?, ?, ?, 'PROPOSED', ?, ?, ?, ?)
                    """, planId, projectId, runId, findingId, planType, requiresApproval,
                    Jsonb.toPgObject(Jsonb.MAPPER.valueToTree(driftService.evidence(projectId, findingId))),
                    text(request, "requestedBy"), "Repair plan proposed for " + finding.get("driftType"));
            eventRepository.record(projectId, null, serviceId, environmentId, null, DeploymentIntentEventType.REPAIR_PLAN_CREATED,
                    text(request, "requestedBy"), "Repair plan proposed", Jsonb.object().put("repairPlanId", planId.toString()));
        }
        jdbcTemplate.update("""
                update reconciliation_runs
                set status = 'SUCCEEDED', completed_at = now(), summary_json = ?
                where id = ?
                """, Jsonb.toPgObject(Jsonb.object().put("issueCount", findings.size()).put("repairPlanCount", findings.size())), runId);
        return evidence(projectId, runId);
    }

    public Map<String, Object> get(UUID projectId, UUID runId) {
        return jdbcTemplate.query(runSelect() + " where project_id = ? and id = ?", ReconciliationRows::run, projectId, runId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECONCILIATION_RUN_NOT_FOUND", "Reconciliation run not found"));
    }

    public Map<String, Object> evidence(UUID projectId, UUID runId) {
        Map<String, Object> run = get(projectId, runId);
        List<Map<String, Object>> issues = jdbcTemplate.query("""
                select id, reconciliation_run_id, project_id, drift_finding_id, issue_type, severity, message,
                    recommended_action, metadata_json::text, created_at
                from reconciliation_issues
                where project_id = ? and reconciliation_run_id = ?
                order by created_at desc
                """, ReconciliationRows::issue, projectId, runId);
        List<Map<String, Object>> plans = jdbcTemplate.query(planSelect() + " where project_id = ? and reconciliation_run_id = ? order by created_at desc",
                ReconciliationRows::plan, projectId, runId);
        return Map.of("run", run, "issues", issues, "repairPlans", plans);
    }

    public List<Map<String, Object>> repairPlans(UUID projectId) {
        return jdbcTemplate.query(planSelect() + " where project_id = ? order by created_at desc", ReconciliationRows::plan, projectId);
    }

    public Map<String, Object> repairPlan(UUID projectId, UUID repairPlanId) {
        return jdbcTemplate.query(planSelect() + " where project_id = ? and id = ?", ReconciliationRows::plan, projectId, repairPlanId)
                .stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPAIR_PLAN_NOT_FOUND", "Repair plan not found"));
    }

    @Transactional
    public Map<String, Object> approve(UUID projectId, UUID repairPlanId, JsonNode request) {
        jdbcTemplate.update("""
                update repair_plans
                set status = 'APPROVED', approved_at = now(), approved_by = ?, approval_reason = ?, updated_at = now()
                where project_id = ? and id = ?
                """, text(request, "approvedBy"), text(request, "reason"), projectId, repairPlanId);
        eventRepository.record(projectId, null, null, null, null, DeploymentIntentEventType.REPAIR_PLAN_APPROVED,
                text(request, "approvedBy"), text(request, "reason"), Jsonb.object().put("repairPlanId", repairPlanId.toString()));
        return repairPlan(projectId, repairPlanId);
    }

    @Transactional
    public Map<String, Object> recommendExecution(UUID projectId, UUID repairPlanId, JsonNode request) {
        Map<String, Object> plan = repairPlan(projectId, repairPlanId);
        if (Boolean.TRUE.equals(plan.get("requiresApproval")) && !"APPROVED".equals(plan.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "REPAIR_PLAN_APPROVAL_REQUIRED", "Repair plan approval is required before execution recommendation");
        }
        jdbcTemplate.update("""
                update repair_plans
                set status = 'EXECUTION_RECOMMENDED', updated_at = now()
                where project_id = ? and id = ?
                """, projectId, repairPlanId);
        ObjectNode payload = Jsonb.object();
        if (plan.get("driftFindingId") != null) {
            payload.put("driftFindingId", plan.get("driftFindingId").toString());
            payload.put("intentType", plan.get("planType").toString());
            payload.put("requestedBy", text(request, "requestedBy"));
            payload.put("reason", text(request, "reason"));
        }
        Map<String, Object> command = commandService.create(projectId, "repair-plan-" + repairPlanId,
                DeploymentCommandService.createCommandBody("CREATE_REPAIR_INTENT", payload, text(request, "requestedBy"), text(request, "reason")));
        eventRepository.record(projectId, null, null, null, null, DeploymentIntentEventType.REPAIR_PLAN_EXECUTION_RECOMMENDED,
                text(request, "requestedBy"), text(request, "reason"), Jsonb.object().put("repairPlanId", repairPlanId.toString())
                        .put("commandId", command.get("commandId").toString()));
        return Map.of("repairPlan", repairPlan(projectId, repairPlanId), "command", command);
    }

    private boolean policyRequiresApproval(UUID projectId, UUID serviceId, UUID environmentId) {
        return jdbcTemplate.query("""
                select require_approval_for_repair
                from reconciliation_policies
                where project_id = ?
                  and (service_id = ? or service_id is null)
                  and (environment_id = ? or environment_id is null)
                order by service_id nulls last, environment_id nulls last
                limit 1
                """, (rs, row) -> rs.getBoolean("require_approval_for_repair"), projectId, serviceId, environmentId)
                .stream().findFirst().orElse(true);
    }

    private static String planType(String action) {
        return switch (action) {
            case "REDEPLOY_DESIRED_ARTIFACT" -> "REDEPLOY_DESIRED_ARTIFACT";
            case "ROLLBACK_TO_STABLE" -> "ROLLBACK_TO_STABLE";
            case "ACCEPT_ACTUAL_AS_DESIRED" -> "ACCEPT_ACTUAL_AS_DESIRED";
            case "MANUAL_INTERVENTION_REQUIRED" -> "MANUAL_INTERVENTION";
            default -> "INVESTIGATE";
        };
    }

    private static String runSelect() {
        return """
                select id, project_id, service_id, environment_id, status, requested_by, reason, idempotency_key,
                    request_hash, started_at, completed_at, summary_json::text
                from reconciliation_runs
                """;
    }

    private static String planSelect() {
        return """
                select id, project_id, reconciliation_run_id, drift_finding_id, plan_type, status, requires_approval,
                    evidence_snapshot_json::text, requested_by, reason, approved_at, approved_by, approval_reason,
                    created_at, updated_at
                from repair_plans
                """;
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && !node.get(field).asText().isBlank()
                ? UUID.fromString(node.get(field).asText()) : null;
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        return node != null && node.hasNonNull(field) ? node.get(field).asBoolean() : fallback;
    }

    private static String text(JsonNode node, String field) {
        String value = node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_FIELD_MISSING", field + " is required");
        }
        return value;
    }

    private static String text(JsonNode node, String field, String fallback) {
        return node != null && node.hasNonNull(field) && !node.get(field).asText().isBlank() ? node.get(field).asText() : fallback;
    }
}
