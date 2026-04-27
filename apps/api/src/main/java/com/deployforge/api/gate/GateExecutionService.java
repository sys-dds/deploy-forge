package com.deployforge.api.gate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.deployforge.api.artifact.ArtifactReadinessStatus;
import com.deployforge.api.artifact.ReleaseArtifactRepository;
import com.deployforge.api.artifact.ReleaseArtifactResponse;
import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.override.CreateDeploymentOverrideRequest;
import com.deployforge.api.override.DeploymentOverrideService;
import com.deployforge.api.override.DeploymentOverrideType;
import com.deployforge.api.plan.DeploymentPlanResponse;
import com.deployforge.api.plan.DeploymentPlanService;
import com.deployforge.api.plan.DeploymentPlanStatus;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GateExecutionService {
    private final DeploymentPlanService planService;
    private final ReleaseArtifactRepository artifactRepository;
    private final GateDefinitionRepository definitionRepository;
    private final GateExecutionRepository executionRepository;
    private final DeploymentOverrideService overrideService;
    private final DeploymentIntentEventRepository eventRepository;

    public GateExecutionService(DeploymentPlanService planService, ReleaseArtifactRepository artifactRepository,
            GateDefinitionRepository definitionRepository, GateExecutionRepository executionRepository,
            DeploymentOverrideService overrideService, DeploymentIntentEventRepository eventRepository) {
        this.planService = planService;
        this.artifactRepository = artifactRepository;
        this.definitionRepository = definitionRepository;
        this.executionRepository = executionRepository;
        this.overrideService = overrideService;
        this.eventRepository = eventRepository;
    }

    public List<GateAttemptResponse> evaluate(UUID projectId, UUID planId, EvaluateGatesRequest request) {
        DeploymentPlanResponse plan = requireRunnablePlan(projectId, planId);
        List<GateDefinitionResponse> definitions = definitions(projectId, plan, request.gateDefinitionIds());
        List<GateAttemptResponse> attempts = new ArrayList<>();
        for (GateDefinitionResponse definition : definitions) {
            attempts.add(run(projectId, plan, definition, request.metrics(), request.requestedBy(), null, null));
        }
        return attempts;
    }

    public List<GateAttemptResponse> evaluateForWave(UUID projectId, UUID planId, UUID rolloutExecutionId,
            UUID rolloutWaveId, EvaluateGatesRequest request) {
        DeploymentPlanResponse plan = requireRunnablePlan(projectId, planId);
        List<GateDefinitionResponse> definitions = definitions(projectId, plan, request.gateDefinitionIds());
        List<GateAttemptResponse> attempts = new ArrayList<>();
        for (GateDefinitionResponse definition : definitions) {
            attempts.add(run(projectId, plan, definition, request.metrics(), request.requestedBy(), rolloutExecutionId, rolloutWaveId));
        }
        return attempts;
    }

    public GateAttemptResponse override(UUID projectId, UUID attemptId, OverrideGateAttemptRequest request) {
        GateAttemptResponse attempt = getAttempt(projectId, attemptId);
        if (attempt.status() != GateAttemptStatus.FAILED && attempt.status() != GateAttemptStatus.TIMED_OUT) {
            throw new ApiException(HttpStatus.CONFLICT, "GATE_OVERRIDE_NOT_ALLOWED", "Only failed or timed-out gates can be overridden");
        }
        overrideService.create(projectId, attempt.deploymentPlanId(),
                new CreateDeploymentOverrideRequest(DeploymentOverrideType.GATE, request.overriddenBy(), request.reason(),
                        request.riskAcknowledgement(), Jsonb.object().put("gateAttemptId", attempt.id().toString())));
        GateAttemptResponse overridden = executionRepository.override(attempt.id(), request.overriddenBy(), request.reason());
        eventRepository.record(projectId, attempt.deploymentPlanId(), null, null, null,
                DeploymentIntentEventType.GATE_OVERRIDDEN, request.overriddenBy(), request.reason(), Jsonb.object());
        return overridden;
    }

    public GateAttemptResponse rerun(UUID projectId, UUID attemptId, RerunGateAttemptRequest request) {
        GateAttemptResponse attempt = getAttempt(projectId, attemptId);
        GateDefinitionResponse definition = definitionRepository.find(projectId, attempt.gateDefinitionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GATE_DEFINITION_NOT_FOUND", "Gate definition not found"));
        DeploymentPlanResponse plan = requireRunnablePlan(projectId, attempt.deploymentPlanId());
        eventRepository.record(projectId, plan.id(), plan.serviceId(), plan.targetEnvironmentId(), plan.artifactId(),
                DeploymentIntentEventType.GATE_RERUN_REQUESTED, request.requestedBy(), request.reason(), Jsonb.object());
        return run(projectId, plan, definition, request.metrics(), request.requestedBy(), null, null);
    }

    public GateEvidenceResponse evidence(UUID projectId, UUID planId) {
        planService.get(projectId, planId);
        List<GateDefinitionResponse> definitions = definitionRepository.list(projectId);
        Map<UUID, GateDefinitionResponse> byId = new LinkedHashMap<>();
        definitions.forEach(definition -> byId.put(definition.id(), definition));
        List<GateAttemptResponse> attempts = executionRepository.listByPlan(projectId, planId);
        if (attempts.isEmpty()) {
            return new GateEvidenceResponse(planId, "PENDING", false, List.of());
        }
        List<GateEvidenceAttemptResponse> rows = attempts.stream()
                .map(attempt -> {
                    GateDefinitionResponse definition = byId.get(attempt.gateDefinitionId());
                    return new GateEvidenceAttemptResponse(attempt.gateDefinitionId(), definition == null ? "unknown" : definition.name(),
                            definition == null ? GateType.SYNTHETIC_CHECK : definition.gateType(),
                            definition != null && definition.required(), attempt.attemptNumber(), attempt.status(),
                            attempt.observed(), attempt.resultSummary(), attempt.failureReason());
                }).toList();
        Map<UUID, GateEvidenceAttemptResponse> latest = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparingInt(GateEvidenceAttemptResponse::attemptNumber))
                .forEach(row -> latest.put(row.gateDefinitionId(), row));
        boolean requiredPassed = latest.values().stream()
                .filter(GateEvidenceAttemptResponse::required)
                .allMatch(row -> row.status() == GateAttemptStatus.PASSED || row.status() == GateAttemptStatus.OVERRIDDEN);
        boolean anyFailed = latest.values().stream().anyMatch(row -> row.status() == GateAttemptStatus.FAILED || row.status() == GateAttemptStatus.TIMED_OUT);
        String overall = requiredPassed ? (anyFailed ? "MIXED" : "PASSED") : "FAILED";
        return new GateEvidenceResponse(planId, overall, requiredPassed, rows);
    }

    public GateEvidenceResponse waveEvidence(UUID projectId, UUID planId, UUID rolloutWaveId) {
        planService.get(projectId, planId);
        List<GateDefinitionResponse> definitions = definitionRepository.list(projectId);
        Map<UUID, GateDefinitionResponse> byId = new LinkedHashMap<>();
        definitions.forEach(definition -> byId.put(definition.id(), definition));
        List<GateAttemptResponse> attempts = executionRepository.listByWave(projectId, rolloutWaveId);
        if (attempts.isEmpty()) {
            return new GateEvidenceResponse(planId, "PENDING", false, List.of());
        }
        List<GateEvidenceAttemptResponse> rows = attempts.stream()
                .map(attempt -> {
                    GateDefinitionResponse definition = byId.get(attempt.gateDefinitionId());
                    return new GateEvidenceAttemptResponse(attempt.gateDefinitionId(), definition == null ? "unknown" : definition.name(),
                            definition == null ? GateType.SYNTHETIC_CHECK : definition.gateType(),
                            definition != null && definition.required(), attempt.attemptNumber(), attempt.status(),
                            attempt.observed(), attempt.resultSummary(), attempt.failureReason());
                }).toList();
        Map<UUID, GateEvidenceAttemptResponse> latest = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparingInt(GateEvidenceAttemptResponse::attemptNumber))
                .forEach(row -> latest.put(row.gateDefinitionId(), row));
        boolean requiredPassed = latest.values().stream()
                .filter(GateEvidenceAttemptResponse::required)
                .allMatch(row -> row.status() == GateAttemptStatus.PASSED || row.status() == GateAttemptStatus.OVERRIDDEN);
        boolean anyRequiredFailed = latest.values().stream()
                .filter(GateEvidenceAttemptResponse::required)
                .anyMatch(row -> row.status() == GateAttemptStatus.FAILED || row.status() == GateAttemptStatus.TIMED_OUT);
        boolean anyFailed = latest.values().stream()
                .anyMatch(row -> row.status() == GateAttemptStatus.FAILED || row.status() == GateAttemptStatus.TIMED_OUT);
        String overall = requiredPassed ? (anyFailed ? "MIXED" : "PASSED") : (anyRequiredFailed ? "FAILED" : "PENDING");
        return new GateEvidenceResponse(planId, overall, requiredPassed, rows);
    }

    private GateAttemptResponse run(UUID projectId, DeploymentPlanResponse plan, GateDefinitionResponse definition,
            Map<String, Double> metrics, String actor, UUID rolloutExecutionId, UUID rolloutWaveId) {
        int attemptNumber = executionRepository.nextAttempt(plan.id(), definition.id());
        GateResult result = switch (definition.gateType()) {
            case HTTP_HEALTH -> http(definition);
            case SYNTHETIC_CHECK -> synthetic(plan, definition);
            case METRIC_THRESHOLD -> metric(definition, metrics == null ? Map.of() : metrics);
        };
        GateAttemptResponse attempt = executionRepository.create(projectId, plan.id(), definition.id(), attemptNumber,
                result.status(), result.observed(), result.summary(), result.failureReason(), rolloutExecutionId, rolloutWaveId);
        eventRepository.record(projectId, plan.id(), plan.serviceId(), plan.targetEnvironmentId(), plan.artifactId(),
                DeploymentIntentEventType.GATE_EVALUATED, actor, result.summary(),
                Jsonb.object().put("gateDefinitionId", definition.id().toString()).put("status", result.status().name()));
        return attempt;
    }

    private GateResult http(GateDefinitionResponse definition) {
        long start = System.nanoTime();
        JsonNode config = definition.config();
        ObjectNode observed = Jsonb.object();
        observed.put("url", config.get("url").asText());
        observed.put("expectedStatus", config.get("expectedStatus").asInt());
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(definition.timeoutSeconds())).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.get("url").asText()))
                    .timeout(Duration.ofSeconds(definition.timeoutSeconds())).GET().build();
            int status = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
            observed.put("actualStatus", status);
            observed.put("durationMs", Duration.ofNanos(System.nanoTime() - start).toMillis());
            boolean passed = status == config.get("expectedStatus").asInt();
            return new GateResult(passed ? GateAttemptStatus.PASSED : GateAttemptStatus.FAILED, observed,
                    passed ? "HTTP health gate passed" : "HTTP health gate failed", passed ? null : "Unexpected HTTP status");
        } catch (HttpTimeoutException exception) {
            observed.put("durationMs", Duration.ofNanos(System.nanoTime() - start).toMillis());
            return new GateResult(GateAttemptStatus.TIMED_OUT, observed, "HTTP health gate timed out", "HTTP request timed out");
        } catch (Exception exception) {
            observed.put("durationMs", Duration.ofNanos(System.nanoTime() - start).toMillis());
            return new GateResult(GateAttemptStatus.FAILED, observed, "HTTP health gate failed", "HTTP request failed");
        }
    }

    private GateResult synthetic(DeploymentPlanResponse plan, GateDefinitionResponse definition) {
        String check = definition.config().get("checkName").asText();
        ObjectNode observed = Jsonb.object().put("checkName", check);
        ReleaseArtifactResponse artifact = artifactRepository.findById(plan.artifactId()).orElseThrow();
        boolean passed = switch (check) {
            case "ARTIFACT_METADATA_PRESENT" -> artifact.metadata() != null && artifact.metadata().size() > 0;
            case "DEPLOYABILITY_PASSED" -> artifact.readinessStatus() == ArtifactReadinessStatus.READY;
            case "ENVIRONMENT_STATE_KNOWN" -> true;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "GATE_CONFIG_INVALID", "Unknown synthetic check");
        };
        observed.put("passed", passed);
        return new GateResult(passed ? GateAttemptStatus.PASSED : GateAttemptStatus.FAILED, observed,
                passed ? "Synthetic check passed" : "Synthetic check failed", passed ? null : "Synthetic check failed");
    }

    private GateResult metric(GateDefinitionResponse definition, Map<String, Double> metrics) {
        JsonNode config = definition.config();
        String name = config.get("metricName").asText();
        String op = config.get("operator").asText();
        double threshold = config.get("threshold").asDouble();
        ObjectNode observed = Jsonb.object().put("metricName", name).put("operator", op).put("threshold", threshold);
        if (!metrics.containsKey(name)) {
            return new GateResult(GateAttemptStatus.FAILED, observed, "Metric threshold gate failed", "Metric value missing");
        }
        double value = metrics.get(name);
        observed.put("observedValue", value);
        boolean passed = switch (op) {
            case "LESS_THAN" -> value < threshold;
            case "LESS_THAN_OR_EQUAL" -> value <= threshold;
            case "GREATER_THAN" -> value > threshold;
            case "GREATER_THAN_OR_EQUAL" -> value >= threshold;
            case "EQUAL" -> Double.compare(value, threshold) == 0;
            default -> false;
        };
        return new GateResult(passed ? GateAttemptStatus.PASSED : GateAttemptStatus.FAILED, observed,
                passed ? "Metric threshold gate passed" : "Metric threshold gate failed", passed ? null : "Metric threshold failed");
    }

    private DeploymentPlanResponse requireRunnablePlan(UUID projectId, UUID planId) {
        DeploymentPlanResponse plan = planService.get(projectId, planId);
        if (plan.status() == DeploymentPlanStatus.CANCELLED || plan.status() == DeploymentPlanStatus.ABORTED) {
            throw new ApiException(HttpStatus.CONFLICT, "GATE_EVALUATION_BLOCKED", "Cancelled or aborted plans cannot evaluate gates");
        }
        return plan;
    }

    private List<GateDefinitionResponse> definitions(UUID projectId, DeploymentPlanResponse plan, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return definitionRepository.enabledForPlan(projectId, plan.targetEnvironmentId());
        }
        return ids.stream().map(id -> {
            GateDefinitionResponse definition = definitionRepository.find(projectId, id)
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GATE_DEFINITION_NOT_FOUND", "Gate definition not found"));
            if (definition.environmentId() != null && !definition.environmentId().equals(plan.targetEnvironmentId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "GATE_ENVIRONMENT_MISMATCH", "Gate does not match the plan target environment");
            }
            return definition;
        }).toList();
    }

    private GateAttemptResponse getAttempt(UUID projectId, UUID attemptId) {
        return executionRepository.find(projectId, attemptId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GATE_ATTEMPT_NOT_FOUND", "Gate attempt not found"));
    }

    private record GateResult(GateAttemptStatus status, JsonNode observed, String summary, String failureReason) {
    }
}
