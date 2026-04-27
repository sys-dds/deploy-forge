package com.deployforge.api.drift;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DriftSeverityCalculator {
    private final JdbcTemplate jdbcTemplate;

    public DriftSeverityCalculator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String severity(UUID environmentId, UUID serviceId, String driftType) {
        var env = jdbcTemplate.queryForMap("select environment_type, protected_environment from deployment_environments where id = ?", environmentId);
        var service = jdbcTemplate.queryForMap("select service_tier from deployable_services where id = ?", serviceId);
        boolean prod = "PROD".equals(env.get("environment_type"));
        boolean protectedEnvironment = Boolean.TRUE.equals(env.get("protected_environment"));
        boolean criticalService = "CRITICAL".equals(service.get("service_tier"));
        if (prod || protectedEnvironment) {
            if ("STALE_TARGET_REPORT".equals(driftType) || "UNKNOWN_ACTUAL_STATE".equals(driftType)
                    || "ARTIFACT_DRIFT".equals(driftType) || "MISSING_DEPLOYMENT".equals(driftType)
                    || "MANUAL_CHANGE".equals(driftType) || ("CONFIG_DRIFT".equals(driftType) && criticalService)) {
                return "CRITICAL";
            }
        }
        if ("UNKNOWN_ACTUAL_STATE".equals(driftType) || "STALE_TARGET_REPORT".equals(driftType)) {
            return "WARNING";
        }
        if ("CONFIG_DRIFT".equals(driftType) && !criticalService) {
            return "WARNING";
        }
        return "DEV".equals(env.get("environment_type")) ? "INFO" : "WARNING";
    }
}
