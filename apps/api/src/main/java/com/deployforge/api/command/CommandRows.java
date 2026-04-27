package com.deployforge.api.command;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;

public final class CommandRows {
    private CommandRows() {
    }

    public static Map<String, Object> command(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("commandId", rs.getObject("id", UUID.class));
        map.put("projectId", rs.getObject("project_id", UUID.class));
        map.put("commandType", rs.getString("command_type"));
        map.put("status", rs.getString("status"));
        map.put("idempotencyKey", rs.getString("idempotency_key"));
        map.put("requestHash", rs.getString("request_hash"));
        map.put("payload", Jsonb.fromString(rs.getString("payload_json")));
        map.put("priority", rs.getInt("priority"));
        map.put("attempts", rs.getInt("attempts"));
        map.put("maxAttempts", rs.getInt("max_attempts"));
        map.put("nextAttemptAt", rs.getObject("next_attempt_at", OffsetDateTime.class));
        map.put("leasedByNodeId", rs.getString("leased_by_node_id"));
        map.put("leaseExpiresAt", rs.getObject("lease_expires_at", OffsetDateTime.class));
        map.put("fencingToken", rs.getLong("fencing_token"));
        map.put("lastError", rs.getString("last_error"));
        map.put("parkedReason", rs.getString("parked_reason"));
        map.put("createdBy", rs.getString("created_by"));
        map.put("reason", rs.getString("reason"));
        map.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
        map.put("updatedAt", rs.getObject("updated_at", OffsetDateTime.class));
        map.put("completedAt", rs.getObject("completed_at", OffsetDateTime.class));
        return map;
    }

    public static Map<String, Object> attempt(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("attemptId", rs.getObject("id", UUID.class));
        map.put("commandId", rs.getObject("command_id", UUID.class));
        map.put("attemptNumber", rs.getInt("attempt_number"));
        map.put("runnerNodeId", rs.getString("runner_node_id"));
        map.put("fencingToken", rs.getLong("fencing_token"));
        map.put("status", rs.getString("status"));
        map.put("startedAt", rs.getObject("started_at", OffsetDateTime.class));
        map.put("completedAt", rs.getObject("completed_at", OffsetDateTime.class));
        map.put("errorMessage", rs.getString("error_message"));
        map.put("result", Jsonb.fromString(rs.getString("result_json")));
        return map;
    }

    public static Map<String, Object> parked(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("parkedCommandId", rs.getObject("id", UUID.class));
        map.put("commandId", rs.getObject("command_id", UUID.class));
        map.put("parkedBy", rs.getString("parked_by"));
        map.put("reason", rs.getString("reason"));
        map.put("parkedAt", rs.getObject("parked_at", OffsetDateTime.class));
        map.put("requeuedAt", rs.getObject("requeued_at", OffsetDateTime.class));
        map.put("requeuedBy", rs.getString("requeued_by"));
        map.put("requeueReason", rs.getString("requeue_reason"));
        return map;
    }
}
