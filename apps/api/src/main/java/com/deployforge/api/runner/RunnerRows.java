package com.deployforge.api.runner;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.shared.Jsonb;

final class RunnerRows {
    private RunnerRows() {
    }

    static Map<String, Object> runner(ResultSet rs, int row) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("runnerId", rs.getObject("id", UUID.class));
        map.put("nodeId", rs.getString("node_id"));
        map.put("status", rs.getString("status"));
        map.put("startedAt", rs.getObject("started_at", OffsetDateTime.class));
        map.put("lastSeenAt", rs.getObject("last_seen_at", OffsetDateTime.class));
        map.put("metadata", Jsonb.fromString(rs.getString("metadata_json")));
        return map;
    }
}
