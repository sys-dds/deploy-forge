package com.deployforge.api.reconcile;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;

final class ReconciliationRows {
    private ReconciliationRows() {
    }

    static Map<String, Object> policy(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("policyId", rs.getObject("id", UUID.class));
        map.put("projectId", rs.getObject("project_id", UUID.class));
        map.put("serviceId", rs.getObject("service_id", UUID.class));
        map.put("environmentId", rs.getObject("environment_id", UUID.class));
        map.put("enabled", rs.getBoolean("enabled"));
        map.put("autoCreateRepairIntents", rs.getBoolean("auto_create_repair_intents"));
        map.put("requireApprovalForRepair", rs.getBoolean("require_approval_for_repair"));
        map.put("maxAutoRepairSeverity", rs.getString("max_auto_repair_severity"));
        map.put("createdBy", rs.getString("created_by"));
        map.put("reason", rs.getString("reason"));
        map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
        map.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
        return map;
    }

    static Map<String, Object> run(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("runId", rs.getObject("id", UUID.class));
        map.put("projectId", rs.getObject("project_id", UUID.class));
        map.put("serviceId", rs.getObject("service_id", UUID.class));
        map.put("environmentId", rs.getObject("environment_id", UUID.class));
        map.put("status", rs.getString("status"));
        map.put("requestedBy", rs.getString("requested_by"));
        map.put("reason", rs.getString("reason"));
        map.put("idempotencyKey", rs.getString("idempotency_key"));
        map.put("requestHash", rs.getString("request_hash"));
        map.put("startedAt", rs.getObject("started_at", OffsetDateTime.class));
        map.put("completedAt", rs.getObject("completed_at", OffsetDateTime.class));
        map.put("summary", Jsonb.fromString(rs.getString("summary_json")));
        return map;
    }

    static Map<String, Object> issue(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issueId", rs.getObject("id", UUID.class));
        map.put("reconciliationRunId", rs.getObject("reconciliation_run_id", UUID.class));
        map.put("projectId", rs.getObject("project_id", UUID.class));
        map.put("driftFindingId", rs.getObject("drift_finding_id", UUID.class));
        map.put("issueType", rs.getString("issue_type"));
        map.put("severity", rs.getString("severity"));
        map.put("message", rs.getString("message"));
        map.put("recommendedAction", rs.getString("recommended_action"));
        map.put("metadata", Jsonb.fromString(rs.getString("metadata_json")));
        map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
        return map;
    }

    static Map<String, Object> plan(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("repairPlanId", rs.getObject("id", UUID.class));
        map.put("projectId", rs.getObject("project_id", UUID.class));
        map.put("reconciliationRunId", rs.getObject("reconciliation_run_id", UUID.class));
        map.put("driftFindingId", rs.getObject("drift_finding_id", UUID.class));
        map.put("planType", rs.getString("plan_type"));
        map.put("status", rs.getString("status"));
        map.put("requiresApproval", rs.getBoolean("requires_approval"));
        map.put("evidenceSnapshot", Jsonb.fromString(rs.getString("evidence_snapshot_json")));
        map.put("requestedBy", rs.getString("requested_by"));
        map.put("reason", rs.getString("reason"));
        map.put("approvedAt", rs.getObject("approved_at", OffsetDateTime.class));
        map.put("approvedBy", rs.getString("approved_by"));
        map.put("approvalReason", rs.getString("approval_reason"));
        map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
        map.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
        return map;
    }
}
