package com.deployforge.api.drift;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriftService {
    private final JdbcTemplate jdbcTemplate;
    private final DeploymentIntentEventRepository eventRepository;
    private final DesiredEnvironmentStateService desiredStateService;
    private final DriftSeverityCalculator severityCalculator;
    private final long staleAfterSeconds;

    public DriftService(JdbcTemplate jdbcTemplate, DeploymentIntentEventRepository eventRepository,
            DesiredEnvironmentStateService desiredStateService, DriftSeverityCalculator severityCalculator,
            @Value("${deployforge.drift.target-stale-after-seconds:300}") long staleAfterSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.eventRepository = eventRepository;
        this.desiredStateService = desiredStateService;
        this.severityCalculator = severityCalculator;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    @Transactional
    public Map<String, Object> registerTarget(UUID projectId, UUID serviceId, UUID environmentId, JsonNode request) {
        validateServiceEnvironment(projectId, serviceId, environmentId);
        String key = text(request, "targetKey");
        Optional<Map<String, Object>> existing = targetByKey(serviceId, environmentId, key);
        if (existing.isPresent()) {
            Map<String, Object> target = existing.get();
            if (target.get("targetType").equals(text(request, "targetType"))
                    && target.get("displayName").equals(text(request, "displayName"))) {
                return target;
            }
            throw new ApiException(HttpStatus.CONFLICT, "RUNTIME_TARGET_KEY_CONFLICT", "Runtime target key already has different identity");
        }
        UUID id = UUID.randomUUID();
        Map<String, Object> target = jdbcTemplate.queryForObject("""
                insert into runtime_targets (id, project_id, service_id, environment_id, target_key, target_type, display_name, status, metadata_json, registered_by)
                values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
                returning id, project_id, service_id, environment_id, target_key, target_type, display_name, status, metadata_json::text, registered_by, created_at
                """, DriftRows::mapTarget, id, projectId, serviceId, environmentId, key, text(request, "targetType"),
                text(request, "displayName"), Jsonb.toPgObject(request.get("metadata")), text(request, "registeredBy"));
        eventRepository.record(projectId, null, serviceId, environmentId, null, DeploymentIntentEventType.RUNTIME_TARGET_REGISTERED,
                text(request, "registeredBy"), "Runtime target registered", Jsonb.object().put("runtimeTargetId", id.toString()));
        return target;
    }

    public List<Map<String, Object>> targets(UUID projectId, UUID serviceId, UUID environmentId) {
        validateServiceEnvironment(projectId, serviceId, environmentId);
        return jdbcTemplate.query("""
                select id, project_id, service_id, environment_id, target_key, target_type, display_name, status, metadata_json::text, registered_by, created_at
                from runtime_targets
                where project_id = ? and service_id = ? and environment_id = ?
                order by target_key
                """, DriftRows::mapTarget, projectId, serviceId, environmentId);
    }

    @Transactional
    public Map<String, Object> heartbeat(UUID projectId, UUID targetId, JsonNode request) {
        Map<String, Object> target = target(projectId, targetId);
        UUID id = UUID.randomUUID();
        Map<String, Object> heartbeat = jdbcTemplate.queryForObject("""
                insert into runtime_target_heartbeats (id, project_id, runtime_target_id, status, reported_by, heartbeat_at, metadata_json)
                values (?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, runtime_target_id, status, reported_by, heartbeat_at, metadata_json::text
                """, DriftRows::mapHeartbeat, id, projectId, targetId, text(request, "status"), text(request, "reportedBy"),
                OffsetDateTime.parse(text(request, "heartbeatAt")), Jsonb.toPgObject(request.get("metadata")));
        eventRepository.record(projectId, null, uuid(target, "serviceId"), uuid(target, "environmentId"), null,
                DeploymentIntentEventType.RUNTIME_TARGET_HEARTBEAT_RECORDED, text(request, "reportedBy"), "Runtime target heartbeat recorded",
                Jsonb.object().put("runtimeTargetId", targetId.toString()));
        return withFreshness(heartbeat);
    }

    public Map<String, Object> latestHeartbeat(UUID projectId, UUID targetId) {
        target(projectId, targetId);
        return latestHeartbeatOptional(projectId, targetId).map(this::withFreshness)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TARGET_HEARTBEAT_NOT_FOUND", "Runtime target heartbeat not found"));
    }

    @Transactional
    public Map<String, Object> deploymentReport(UUID projectId, UUID targetId, JsonNode request) {
        Map<String, Object> target = target(projectId, targetId);
        UUID artifactId = uuidOrNull(request, "reportedArtifactId");
        if (artifactId != null) {
            validateArtifact(projectId, uuid(target, "serviceId"), artifactId);
        }
        if ("RUNNING".equals(text(request, "reportStatus")) && blank(request.path("reportedImageDigest").asText(null))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RUNNING_REPORT_DIGEST_REQUIRED", "Running report requires image digest");
        }
        UUID id = UUID.randomUUID();
        Map<String, Object> report = jdbcTemplate.queryForObject("""
                insert into runtime_deployment_reports (
                    id, project_id, runtime_target_id, service_id, environment_id, reported_artifact_id,
                    reported_image_digest, reported_version, report_status, reported_by, observed_at, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, runtime_target_id, service_id, environment_id, reported_artifact_id,
                    reported_image_digest, reported_version, report_status, reported_by, observed_at, metadata_json::text
                """, DriftRows::mapDeploymentReport, id, projectId, targetId, uuid(target, "serviceId"), uuid(target, "environmentId"),
                artifactId, nullableText(request, "reportedImageDigest"), nullableText(request, "reportedVersion"),
                text(request, "reportStatus"), text(request, "reportedBy"), OffsetDateTime.parse(text(request, "observedAt")),
                Jsonb.toPgObject(request.get("metadata")));
        eventRepository.record(projectId, null, uuid(target, "serviceId"), uuid(target, "environmentId"), artifactId,
                DeploymentIntentEventType.ACTUAL_STATE_REPORTED, text(request, "reportedBy"), "Actual deployment state reported",
                Jsonb.object().put("runtimeTargetId", targetId.toString()).put("reportId", id.toString()));
        return report;
    }

    public Map<String, Object> latestDeploymentReport(UUID projectId, UUID targetId) {
        target(projectId, targetId);
        return latestDeploymentReportOptional(projectId, targetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEPLOYMENT_REPORT_NOT_FOUND", "Runtime deployment report not found"));
    }

    @Transactional
    public Map<String, Object> configReport(UUID projectId, UUID targetId, JsonNode request) {
        Map<String, Object> target = target(projectId, targetId);
        UUID id = UUID.randomUUID();
        Map<String, Object> report = jdbcTemplate.queryForObject("""
                insert into runtime_config_reports (
                    id, project_id, runtime_target_id, service_id, environment_id, config_version,
                    config_digest, report_status, reported_by, observed_at, metadata_json
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                returning id, project_id, runtime_target_id, service_id, environment_id, config_version,
                    config_digest, report_status, reported_by, observed_at, metadata_json::text
                """, DriftRows::mapConfigReport, id, projectId, targetId, uuid(target, "serviceId"), uuid(target, "environmentId"),
                nullableText(request, "configVersion"), nullableText(request, "configDigest"), text(request, "reportStatus"),
                text(request, "reportedBy"), OffsetDateTime.parse(text(request, "observedAt")), Jsonb.toPgObject(request.get("metadata")));
        eventRepository.record(projectId, null, uuid(target, "serviceId"), uuid(target, "environmentId"), null,
                DeploymentIntentEventType.CONFIG_STATE_REPORTED, text(request, "reportedBy"), "Runtime config state reported",
                Jsonb.object().put("runtimeTargetId", targetId.toString()).put("reportId", id.toString()));
        return report;
    }

    public Map<String, Object> latestConfigReport(UUID projectId, UUID targetId) {
        target(projectId, targetId);
        return latestConfigReportOptional(projectId, targetId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CONFIG_REPORT_NOT_FOUND", "Runtime config report not found"));
    }

    @Transactional
    public Map<String, Object> check(UUID projectId, UUID serviceId, UUID environmentId, JsonNode request) {
        validateServiceEnvironment(projectId, serviceId, environmentId);
        eventRepository.record(projectId, null, serviceId, environmentId, null, DeploymentIntentEventType.DRIFT_VERIFIER_RUN,
                text(request, "requestedBy"), text(request, "reason"), Jsonb.object());
        Optional<Map<String, Object>> desired = desiredStateService.find(projectId, serviceId, environmentId);
        List<Map<String, Object>> findings = new ArrayList<>();
        for (Map<String, Object> target : targets(projectId, serviceId, environmentId)) {
            if ("INACTIVE".equals(target.get("status"))) {
                continue;
            }
            findings.addAll(detectForTarget(projectId, serviceId, environmentId, desired.orElse(null), target, text(request, "requestedBy")));
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("projectId", projectId);
        response.put("serviceId", serviceId);
        response.put("environmentId", environmentId);
        response.put("findingsCreatedOrUpdated", findings.size());
        response.put("findings", findings);
        return response;
    }

    public List<Map<String, Object>> findings(UUID projectId, UUID serviceId, UUID environmentId, String status, String driftType, String severity) {
        StringBuilder sql = new StringBuilder("""
                select id, project_id, service_id, environment_id, runtime_target_id, desired_state_id, drift_type, severity, status,
                    desired_json::text, actual_json::text, message, recommended_action, first_detected_at, last_detected_at,
                    acknowledged_by, acknowledgement_reason, resolved_by, resolution_reason
                from drift_findings
                where project_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(projectId);
        if (serviceId != null) {
            sql.append(" and service_id = ?");
            args.add(serviceId);
        }
        if (environmentId != null) {
            sql.append(" and environment_id = ?");
            args.add(environmentId);
        }
        if (!blank(status)) {
            sql.append(" and status = ?");
            args.add(status);
        }
        if (!blank(driftType)) {
            sql.append(" and drift_type = ?");
            args.add(driftType);
        }
        if (!blank(severity)) {
            sql.append(" and severity = ?");
            args.add(severity);
        }
        sql.append(" order by first_detected_at desc");
        return jdbcTemplate.query(sql.toString(), DriftRows::mapFinding, args.toArray());
    }

    public Map<String, Object> finding(UUID projectId, UUID findingId) {
        return jdbcTemplate.query("""
                select id, project_id, service_id, environment_id, runtime_target_id, desired_state_id, drift_type, severity, status,
                    desired_json::text, actual_json::text, message, recommended_action, first_detected_at, last_detected_at,
                    acknowledged_by, acknowledgement_reason, resolved_by, resolution_reason
                from drift_findings where project_id = ? and id = ?
                """, DriftRows::mapFinding, projectId, findingId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DRIFT_FINDING_NOT_FOUND", "Drift finding not found"));
    }

    @Transactional
    public Map<String, Object> acknowledge(UUID projectId, UUID findingId, JsonNode request, boolean manualChange) {
        Map<String, Object> finding = finding(projectId, findingId);
        if ("RESOLVED".equals(finding.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "DRIFT_ALREADY_RESOLVED", "Resolved drift cannot be acknowledged");
        }
        if (manualChange && !List.of("MANUAL_CHANGE", "ARTIFACT_DRIFT").contains(finding.get("driftType"))) {
            throw new ApiException(HttpStatus.CONFLICT, "MANUAL_CHANGE_ACK_NOT_ALLOWED", "Only manual or artifact drift can be acknowledged as manual change");
        }
        String actor = text(request, manualChange ? "acknowledgedBy" : "acknowledgedBy");
        String reason = text(request, "reason");
        jdbcTemplate.update("""
                update drift_findings set status = 'ACKNOWLEDGED', acknowledged_at = coalesce(acknowledged_at, now()),
                    acknowledged_by = coalesce(acknowledged_by, ?), acknowledgement_reason = coalesce(acknowledgement_reason, ?)
                where id = ?
                """, actor, reason, findingId);
        eventRepository.record(projectId, null, uuid(finding, "serviceId"), uuid(finding, "environmentId"), null,
                manualChange ? DeploymentIntentEventType.MANUAL_CHANGE_ACKNOWLEDGED : DeploymentIntentEventType.DRIFT_ACKNOWLEDGED,
                actor, reason, Jsonb.object().put("driftFindingId", findingId.toString()));
        return finding(projectId, findingId);
    }

    @Transactional
    public Map<String, Object> resolve(UUID projectId, UUID findingId, JsonNode request) {
        Map<String, Object> finding = finding(projectId, findingId);
        if ("RESOLVED".equals(finding.get("status"))) {
            return finding;
        }
        String actor = text(request, "resolvedBy");
        String reason = text(request, "reason");
        jdbcTemplate.update("""
                update drift_findings set status = 'RESOLVED', resolved_at = now(), resolved_by = ?, resolution_reason = ?
                where id = ?
                """, actor, reason, findingId);
        eventRepository.record(projectId, null, uuid(finding, "serviceId"), uuid(finding, "environmentId"), null,
                DeploymentIntentEventType.DRIFT_RESOLVED, actor, reason, Jsonb.object().put("driftFindingId", findingId.toString()));
        return finding(projectId, findingId);
    }

    @Transactional
    public Map<String, Object> repairIntent(UUID projectId, UUID findingId, JsonNode request) {
        Map<String, Object> finding = finding(projectId, findingId);
        if ("RESOLVED".equals(finding.get("status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "DRIFT_RESOLVED", "Resolved drift cannot accept new repair intent");
        }
        String intentType = text(request, "intentType");
        try {
            UUID id = UUID.randomUUID();
            Map<String, Object> intent = jdbcTemplate.queryForObject("""
                    insert into drift_repair_intents (id, project_id, drift_finding_id, intent_type, status, requested_by, reason, metadata_json)
                    values (?, ?, ?, ?, 'OPEN', ?, ?, ?)
                    returning id, project_id, drift_finding_id, intent_type, status, requested_by, reason, metadata_json::text, created_at
                    """, DriftRows::mapRepairIntent, id, projectId, findingId, intentType, text(request, "requestedBy"),
                    text(request, "reason"), Jsonb.toPgObject(request.get("metadata")));
            eventRepository.record(projectId, null, uuid(finding, "serviceId"), uuid(finding, "environmentId"), null,
                    DeploymentIntentEventType.DRIFT_REPAIR_INTENT_CREATED, text(request, "requestedBy"), text(request, "reason"),
                    Jsonb.object().put("driftFindingId", findingId.toString()).put("intentType", intentType));
            return intent;
        } catch (DuplicateKeyException exception) {
            return repairIntents(projectId, findingId).stream().filter(intent -> intentType.equals(intent.get("intentType")))
                    .findFirst().orElseThrow();
        }
    }

    public List<Map<String, Object>> repairIntents(UUID projectId, UUID findingId) {
        finding(projectId, findingId);
        return jdbcTemplate.query("""
                select id, project_id, drift_finding_id, intent_type, status, requested_by, reason, metadata_json::text, created_at
                from drift_repair_intents where project_id = ? and drift_finding_id = ? order by created_at
                """, DriftRows::mapRepairIntent, projectId, findingId);
    }

    public Map<String, Object> evidence(UUID projectId, UUID findingId) {
        Map<String, Object> finding = finding(projectId, findingId);
        UUID targetId = uuid(finding, "runtimeTargetId");
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("finding", finding);
        evidence.put("desiredState", desiredStateService.find(projectId, uuid(finding, "serviceId"), uuid(finding, "environmentId")).orElse(null));
        evidence.put("runtimeTarget", targetId == null ? null : target(projectId, targetId));
        evidence.put("latestHeartbeat", targetId == null ? null : latestHeartbeatOptional(projectId, targetId).map(this::withFreshness).orElse(null));
        evidence.put("latestDeploymentReport", targetId == null ? null : latestDeploymentReportOptional(projectId, targetId).orElse(null));
        evidence.put("latestConfigReport", targetId == null ? null : latestConfigReportOptional(projectId, targetId).orElse(null));
        evidence.put("repairRecommendation", finding.get("recommendedAction"));
        evidence.put("repairIntents", repairIntents(projectId, findingId));
        evidence.put("events", eventRepository.find(projectId, null, null));
        evidence.put("recommendedNextAction", finding.get("recommendedAction"));
        return evidence;
    }

    @Transactional
    public Map<String, Object> acceptActualAsDesired(UUID projectId, UUID findingId, JsonNode request) {
        Map<String, Object> finding = finding(projectId, findingId);
        if (!List.of("MANUAL_CHANGE", "ARTIFACT_DRIFT").contains(finding.get("driftType"))) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCEPT_ACTUAL_NOT_ALLOWED", "Only manual or artifact drift can be accepted");
        }
        JsonNode actual = (JsonNode) finding.get("actual");
        UUID artifactId = actual.hasNonNull("reportedArtifactId") ? UUID.fromString(actual.get("reportedArtifactId").asText()) : null;
        String digest = actual.path("reportedDigest").asText(null);
        if (artifactId == null && blank(digest)) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCEPT_ACTUAL_NEEDS_ARTIFACT_OR_DIGEST", "Actual report must include artifact or digest");
        }
        desiredStateService.acceptActual(projectId, uuid(finding, "serviceId"), uuid(finding, "environmentId"), artifactId, digest,
                text(request, "acceptedBy"), text(request, "reason"));
        resolve(projectId, findingId, Jsonb.object().put("resolvedBy", text(request, "acceptedBy")).put("reason", text(request, "reason")));
        eventRepository.record(projectId, null, uuid(finding, "serviceId"), uuid(finding, "environmentId"), artifactId,
                DeploymentIntentEventType.ACTUAL_ACCEPTED_AS_DESIRED, text(request, "acceptedBy"), text(request, "reason"),
                Jsonb.object().put("driftFindingId", findingId.toString()));
        return desiredStateService.get(projectId, uuid(finding, "serviceId"), uuid(finding, "environmentId"));
    }

    @Transactional
    public Map<String, Object> recommendRedeploy(UUID projectId, UUID findingId, JsonNode request) {
        ObjectNode body = Jsonb.object().put("intentType", "REDEPLOY_DESIRED_ARTIFACT")
                .put("requestedBy", text(request, "requestedBy")).put("reason", text(request, "reason"));
        Map<String, Object> intent = repairIntent(projectId, findingId, body);
        Map<String, Object> finding = finding(projectId, findingId);
        eventRepository.record(projectId, null, uuid(finding, "serviceId"), uuid(finding, "environmentId"), null,
                DeploymentIntentEventType.REDEPLOY_DESIRED_RECOMMENDED, text(request, "requestedBy"), text(request, "reason"),
                Jsonb.object().put("driftFindingId", findingId.toString()));
        return intent;
    }

    public Map<String, Object> actualState(UUID projectId, UUID serviceId, UUID environmentId) {
        validateServiceEnvironment(projectId, serviceId, environmentId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("projectId", projectId);
        response.put("serviceId", serviceId);
        response.put("environmentId", environmentId);
        response.put("desired", desiredStateService.find(projectId, serviceId, environmentId).orElse(null));
        List<Map<String, Object>> targetStates = new ArrayList<>();
        boolean drifted = false;
        boolean stale = false;
        boolean unknown = false;
        for (Map<String, Object> target : targets(projectId, serviceId, environmentId)) {
            UUID targetId = uuid(target, "runtimeTargetId");
            Map<String, Object> item = new LinkedHashMap<>(target);
            Map<String, Object> heartbeat = latestHeartbeatOptional(projectId, targetId).map(this::withFreshness).orElse(null);
            item.put("latestHeartbeat", heartbeat);
            item.put("freshness", heartbeat == null ? "UNKNOWN" : heartbeat.get("freshness"));
            item.put("latestDeploymentReport", latestDeploymentReportOptional(projectId, targetId).orElse(null));
            item.put("latestConfigReport", latestConfigReportOptional(projectId, targetId).orElse(null));
            List<Map<String, Object>> open = findings(projectId, serviceId, environmentId, "OPEN", null, null).stream()
                    .filter(f -> targetId.equals(f.get("runtimeTargetId"))).toList();
            item.put("openDriftFindings", open);
            drifted |= !open.isEmpty();
            stale |= "STALE".equals(item.get("freshness"));
            unknown |= item.get("latestDeploymentReport") == null;
            targetStates.add(item);
        }
        response.put("targets", targetStates);
        response.put("overallActualStatus", drifted ? "DRIFTED" : stale ? "STALE" : unknown ? "UNKNOWN" : "MATCHING");
        response.put("recommendedAction", drifted ? "REDEPLOY_DESIRED_ARTIFACT" : stale || unknown ? "INVESTIGATE" : "NONE");
        return response;
    }

    public boolean hasBlockingDrift(UUID projectId, UUID serviceId, UUID environmentId) {
        return !jdbcTemplate.queryForList("""
                select id from drift_findings
                where project_id = ? and service_id = ? and environment_id = ? and status in ('OPEN','ACKNOWLEDGED') and severity = 'CRITICAL'
                limit 1
                """, UUID.class, projectId, serviceId, environmentId).isEmpty();
    }

    private List<Map<String, Object>> detectForTarget(UUID projectId, UUID serviceId, UUID environmentId, Map<String, Object> desired,
            Map<String, Object> target, String actor) {
        UUID targetId = uuid(target, "runtimeTargetId");
        List<Map<String, Object>> findings = new ArrayList<>();
        Optional<Map<String, Object>> heartbeat = latestHeartbeatOptional(projectId, targetId);
        boolean stale = heartbeat.isEmpty() || "STALE".equals(withFreshness(heartbeat.get()).get("freshness"));
        if (stale) {
            findings.add(upsertFinding(projectId, serviceId, environmentId, targetId, desired, "STALE_TARGET_REPORT",
                    desiredJson(desired), Jsonb.object().put("runtimeTargetId", targetId.toString()), actor));
            return findings;
        }
        Optional<Map<String, Object>> report = latestDeploymentReportOptional(projectId, targetId);
        if (desired != null && report.isEmpty()) {
            findings.add(upsertFinding(projectId, serviceId, environmentId, targetId, desired, "UNKNOWN_ACTUAL_STATE",
                    desiredJson(desired), Jsonb.object().put("runtimeTargetId", targetId.toString()), actor));
            return findings;
        }
        if (desired != null && report.isPresent()) {
            Map<String, Object> actual = report.get();
            if ("MISSING".equals(actual.get("reportStatus"))) {
                findings.add(upsertFinding(projectId, serviceId, environmentId, targetId, desired, "MISSING_DEPLOYMENT",
                        desiredJson(desired), actualJson(actual), actor));
            } else if ("UNKNOWN".equals(actual.get("reportStatus")) || "ERROR".equals(actual.get("reportStatus"))) {
                findings.add(upsertFinding(projectId, serviceId, environmentId, targetId, desired, "UNKNOWN_ACTUAL_STATE",
                        desiredJson(desired), actualJson(actual), actor));
            } else if (!sameDigest((String) desired.get("desiredImageDigest"), (String) actual.get("reportedImageDigest"))) {
                String type = actual.get("reportedArtifactId") != null ? "MANUAL_CHANGE" : "ARTIFACT_DRIFT";
                findings.add(upsertFinding(projectId, serviceId, environmentId, targetId, desired, type,
                        desiredJson(desired), actualJson(actual), actor));
            }
        }
        Optional<Map<String, Object>> config = latestConfigReportOptional(projectId, targetId);
        if (desired != null && !blank((String) desired.get("desiredConfigVersion")) && config.isPresent()) {
            Map<String, Object> actualConfig = config.get();
            if ("MISSING".equals(actualConfig.get("reportStatus"))
                    || !sameDigest((String) desired.get("desiredConfigVersion"), (String) actualConfig.get("configVersion"))) {
                findings.add(upsertFinding(projectId, serviceId, environmentId, targetId, desired, "CONFIG_DRIFT",
                        desiredJson(desired), configJson(actualConfig), actor));
            }
        }
        return findings;
    }

    private Map<String, Object> upsertFinding(UUID projectId, UUID serviceId, UUID environmentId, UUID targetId, Map<String, Object> desired,
            String driftType, JsonNode desiredJson, JsonNode actualJson, String actor) {
        String severity = severityCalculator.severity(environmentId, serviceId, driftType);
        String recommendation = recommendation(driftType);
        String message = driftType + " detected for runtime target";
        List<Map<String, Object>> existing = jdbcTemplate.query("""
                select id, project_id, service_id, environment_id, runtime_target_id, desired_state_id, drift_type, severity, status,
                    desired_json::text, actual_json::text, message, recommended_action, first_detected_at, last_detected_at,
                    acknowledged_by, acknowledgement_reason, resolved_by, resolution_reason
                from drift_findings
                where project_id = ? and service_id = ? and environment_id = ? and runtime_target_id = ? and drift_type = ? and status in ('OPEN','ACKNOWLEDGED')
                """, DriftRows::mapFinding, projectId, serviceId, environmentId, targetId, driftType);
        if (!existing.isEmpty()) {
            UUID id = uuid(existing.get(0), "driftFindingId");
            jdbcTemplate.update("""
                    update drift_findings set severity = ?, desired_json = ?, actual_json = ?, message = ?,
                        recommended_action = ?, last_detected_at = now()
                    where id = ?
                    """, severity, Jsonb.toPgObject(desiredJson), Jsonb.toPgObject(actualJson), message, recommendation, id);
            return finding(projectId, id);
        }
        UUID id = UUID.randomUUID();
        Map<String, Object> inserted = jdbcTemplate.queryForObject("""
                insert into drift_findings (
                    id, project_id, service_id, environment_id, runtime_target_id, desired_state_id, drift_type,
                    severity, status, desired_json, actual_json, message, recommended_action
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?)
                returning id, project_id, service_id, environment_id, runtime_target_id, desired_state_id, drift_type, severity, status,
                    desired_json::text, actual_json::text, message, recommended_action, first_detected_at, last_detected_at,
                    acknowledged_by, acknowledgement_reason, resolved_by, resolution_reason
                """, DriftRows::mapFinding, id, projectId, serviceId, environmentId, targetId,
                desired == null ? null : desired.get("desiredStateId"), driftType, severity, Jsonb.toPgObject(desiredJson),
                Jsonb.toPgObject(actualJson), message, recommendation);
        eventRepository.record(projectId, null, serviceId, environmentId, null, DeploymentIntentEventType.DRIFT_DETECTED,
                blank(actor) ? "system" : actor, message, Jsonb.object().put("driftFindingId", id.toString()).put("driftType", driftType));
        return inserted;
    }

    private String recommendation(String driftType) {
        return switch (driftType) {
            case "ARTIFACT_DRIFT", "MISSING_DEPLOYMENT" -> "REDEPLOY_DESIRED_ARTIFACT";
            case "MANUAL_CHANGE" -> "ACCEPT_ACTUAL_AS_DESIRED";
            case "CONFIG_DRIFT", "STALE_TARGET_REPORT", "UNKNOWN_ACTUAL_STATE" -> "INVESTIGATE";
            case "ROLLBACK_MISMATCH" -> "MANUAL_INTERVENTION_REQUIRED";
            default -> "NONE";
        };
    }

    private ObjectNode desiredJson(Map<String, Object> desired) {
        ObjectNode node = Jsonb.object();
        if (desired != null) {
            put(node, "desiredArtifactId", desired.get("desiredArtifactId"));
            put(node, "desiredDigest", desired.get("desiredImageDigest"));
            put(node, "desiredConfigVersion", desired.get("desiredConfigVersion"));
        }
        return node;
    }

    private ObjectNode actualJson(Map<String, Object> actual) {
        ObjectNode node = Jsonb.object();
        put(node, "reportedArtifactId", actual.get("reportedArtifactId"));
        put(node, "reportedDigest", actual.get("reportedImageDigest"));
        put(node, "reportStatus", actual.get("reportStatus"));
        return node;
    }

    private ObjectNode configJson(Map<String, Object> actual) {
        ObjectNode node = Jsonb.object();
        put(node, "configVersion", actual.get("configVersion"));
        put(node, "configDigest", actual.get("configDigest"));
        put(node, "reportStatus", actual.get("reportStatus"));
        return node;
    }

    private Optional<Map<String, Object>> latestHeartbeatOptional(UUID projectId, UUID targetId) {
        return jdbcTemplate.query("""
                select id, project_id, runtime_target_id, status, reported_by, heartbeat_at, metadata_json::text
                from runtime_target_heartbeats
                where project_id = ? and runtime_target_id = ?
                order by heartbeat_at desc, created_at desc limit 1
                """, DriftRows::mapHeartbeat, projectId, targetId).stream().findFirst();
    }

    private Optional<Map<String, Object>> latestDeploymentReportOptional(UUID projectId, UUID targetId) {
        return jdbcTemplate.query("""
                select id, project_id, runtime_target_id, service_id, environment_id, reported_artifact_id,
                    reported_image_digest, reported_version, report_status, reported_by, observed_at, metadata_json::text
                from runtime_deployment_reports
                where project_id = ? and runtime_target_id = ?
                order by observed_at desc, created_at desc limit 1
                """, DriftRows::mapDeploymentReport, projectId, targetId).stream().findFirst();
    }

    private Optional<Map<String, Object>> latestConfigReportOptional(UUID projectId, UUID targetId) {
        return jdbcTemplate.query("""
                select id, project_id, runtime_target_id, service_id, environment_id, config_version,
                    config_digest, report_status, reported_by, observed_at, metadata_json::text
                from runtime_config_reports
                where project_id = ? and runtime_target_id = ?
                order by observed_at desc, created_at desc limit 1
                """, DriftRows::mapConfigReport, projectId, targetId).stream().findFirst();
    }

    private Map<String, Object> withFreshness(Map<String, Object> heartbeat) {
        Map<String, Object> copy = new LinkedHashMap<>(heartbeat);
        OffsetDateTime at = (OffsetDateTime) heartbeat.get("heartbeatAt");
        copy.put("freshness", at.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(staleAfterSeconds)) ? "STALE" : "FRESH");
        return copy;
    }

    private Optional<Map<String, Object>> targetByKey(UUID serviceId, UUID environmentId, String key) {
        return jdbcTemplate.query("""
                select id, project_id, service_id, environment_id, target_key, target_type, display_name, status, metadata_json::text, registered_by, created_at
                from runtime_targets where service_id = ? and environment_id = ? and target_key = ?
                """, DriftRows::mapTarget, serviceId, environmentId, key).stream().findFirst();
    }

    private Map<String, Object> target(UUID projectId, UUID targetId) {
        return jdbcTemplate.query("""
                select id, project_id, service_id, environment_id, target_key, target_type, display_name, status, metadata_json::text, registered_by, created_at
                from runtime_targets where project_id = ? and id = ?
                """, DriftRows::mapTarget, projectId, targetId).stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RUNTIME_TARGET_NOT_FOUND", "Runtime target not found"));
    }

    private void validateServiceEnvironment(UUID projectId, UUID serviceId, UUID environmentId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from deployable_services s, deployment_environments e
                where s.id = ? and e.id = ? and s.project_id = ? and e.project_id = ?
                """, Integer.class, serviceId, environmentId, projectId, projectId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SERVICE_ENVIRONMENT_NOT_FOUND", "Service/environment relation is invalid");
        }
    }

    private void validateArtifact(UUID projectId, UUID serviceId, UUID artifactId) {
        Integer count = jdbcTemplate.queryForObject("select count(*) from release_artifacts where id = ? and project_id = ? and service_id = ?",
                Integer.class, artifactId, projectId, serviceId);
        if (count == null || count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REPORT_ARTIFACT_SERVICE_MISMATCH", "Reported artifact must belong to the target service");
        }
    }

    private static UUID uuid(Map<String, Object> map, String key) {
        return (UUID) map.get(key);
    }

    private static UUID uuidOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) && !node.get(field).asText().isBlank() ? UUID.fromString(node.get(field).asText()) : null;
    }

    private static String text(JsonNode node, String field) {
        String value = nullableText(node, field);
        if (blank(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_FIELD_MISSING", field + " is required");
        }
        return value;
    }

    private static String nullableText(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
    }

    private static boolean sameDigest(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void put(ObjectNode node, String field, Object value) {
        if (value instanceof UUID uuid) {
            node.put(field, uuid.toString());
        } else if (value instanceof String string) {
            node.put(field, string);
        } else if (value != null) {
            node.put(field, value.toString());
        }
    }
}
