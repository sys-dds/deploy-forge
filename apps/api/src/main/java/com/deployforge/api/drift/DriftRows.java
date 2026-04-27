package com.deployforge.api.drift;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;

final class DriftRows {
    private DriftRows() {
    }

    static Map<String, Object> mapDesired(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = baseIds(rs);
        map.put("desiredStateId", rs.getObject("id", UUID.class));
        map.put("desiredArtifactId", rs.getObject("desired_artifact_id", UUID.class));
        map.put("desiredImageDigest", rs.getString("desired_image_digest"));
        map.put("desiredConfigVersion", rs.getString("desired_config_version"));
        map.put("desiredSource", rs.getString("desired_source"));
        map.put("desiredReason", rs.getString("desired_reason"));
        map.put("recordedBy", rs.getString("recorded_by"));
        map.put("rolloutExecutionId", rs.getObject("rollout_execution_id", UUID.class));
        map.put("rollbackExecutionId", rs.getObject("rollback_execution_id", UUID.class));
        map.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
        return map;
    }

    static Map<String, Object> mapTarget(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = baseIds(rs);
        map.put("runtimeTargetId", rs.getObject("id", UUID.class));
        map.put("targetKey", rs.getString("target_key"));
        map.put("targetType", rs.getString("target_type"));
        map.put("displayName", rs.getString("display_name"));
        map.put("status", rs.getString("status"));
        map.put("metadata", Jsonb.fromString(rs.getString("metadata_json")));
        map.put("registeredBy", rs.getString("registered_by"));
        map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
        return map;
    }

    static Map<String, Object> mapHeartbeat(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", rs.getObject("id", UUID.class));
        map.put("projectId", rs.getObject("project_id", UUID.class));
        map.put("runtimeTargetId", rs.getObject("runtime_target_id", UUID.class));
        map.put("status", rs.getString("status"));
        map.put("reportedBy", rs.getString("reported_by"));
        map.put("heartbeatAt", rs.getObject("heartbeat_at", OffsetDateTime.class));
        map.put("metadata", Jsonb.fromString(rs.getString("metadata_json")));
        return map;
    }

    static Map<String, Object> mapDeploymentReport(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = baseReport(rs);
        map.put("reportedArtifactId", rs.getObject("reported_artifact_id", UUID.class));
        map.put("reportedImageDigest", rs.getString("reported_image_digest"));
        map.put("reportedVersion", rs.getString("reported_version"));
        return map;
    }

    static Map<String, Object> mapConfigReport(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = baseReport(rs);
        map.put("configVersion", rs.getString("config_version"));
        map.put("configDigest", rs.getString("config_digest"));
        return map;
    }

    static Map<String, Object> mapFinding(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = baseIds(rs);
        map.put("driftFindingId", rs.getObject("id", UUID.class));
        map.put("runtimeTargetId", rs.getObject("runtime_target_id", UUID.class));
        map.put("desiredStateId", rs.getObject("desired_state_id", UUID.class));
        map.put("driftType", rs.getString("drift_type"));
        map.put("severity", rs.getString("severity"));
        map.put("status", rs.getString("status"));
        map.put("desired", Jsonb.fromString(rs.getString("desired_json")));
        map.put("actual", Jsonb.fromString(rs.getString("actual_json")));
        map.put("message", rs.getString("message"));
        map.put("recommendedAction", rs.getString("recommended_action"));
        map.put("firstDetectedAt", rs.getObject("first_detected_at", OffsetDateTime.class));
        map.put("lastDetectedAt", rs.getObject("last_detected_at", OffsetDateTime.class));
        map.put("acknowledgedBy", rs.getString("acknowledged_by"));
        map.put("acknowledgementReason", rs.getString("acknowledgement_reason"));
        map.put("resolvedBy", rs.getString("resolved_by"));
        map.put("resolutionReason", rs.getString("resolution_reason"));
        return map;
    }

    static Map<String, Object> mapRepairIntent(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("repairIntentId", rs.getObject("id", UUID.class));
        map.put("projectId", rs.getObject("project_id", UUID.class));
        map.put("driftFindingId", rs.getObject("drift_finding_id", UUID.class));
        map.put("intentType", rs.getString("intent_type"));
        map.put("status", rs.getString("status"));
        map.put("requestedBy", rs.getString("requested_by"));
        map.put("reason", rs.getString("reason"));
        map.put("metadata", Jsonb.fromString(rs.getString("metadata_json")));
        map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
        return map;
    }

    private static Map<String, Object> baseIds(ResultSet rs) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectId", rs.getObject("project_id", UUID.class));
        map.put("serviceId", rs.getObject("service_id", UUID.class));
        map.put("environmentId", rs.getObject("environment_id", UUID.class));
        return map;
    }

    private static Map<String, Object> baseReport(ResultSet rs) throws SQLException {
        Map<String, Object> map = baseIds(rs);
        map.put("reportId", rs.getObject("id", UUID.class));
        map.put("runtimeTargetId", rs.getObject("runtime_target_id", UUID.class));
        map.put("reportStatus", rs.getString("report_status"));
        map.put("reportedBy", rs.getString("reported_by"));
        map.put("observedAt", rs.getObject("observed_at", OffsetDateTime.class));
        map.put("metadata", Jsonb.fromString(rs.getString("metadata_json")));
        return map;
    }
}
