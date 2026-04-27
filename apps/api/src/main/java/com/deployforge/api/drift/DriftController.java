package com.deployforge.api.drift;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DriftController {
    private final DriftService driftService;
    private final DesiredEnvironmentStateService desiredStateService;

    public DriftController(DriftService driftService, DesiredEnvironmentStateService desiredStateService) {
        this.driftService = driftService;
        this.desiredStateService = desiredStateService;
    }

    @GetMapping("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/desired-state")
    public Map<String, Object> desiredState(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID environmentId) {
        return desiredStateService.get(projectId, serviceId, environmentId);
    }

    @PostMapping("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/runtime-targets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> registerTarget(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID environmentId, @RequestBody JsonNode request) {
        return driftService.registerTarget(projectId, serviceId, environmentId, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/runtime-targets")
    public List<Map<String, Object>> targets(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID environmentId) {
        return driftService.targets(projectId, serviceId, environmentId);
    }

    @PostMapping("/api/v1/projects/{projectId}/runtime-targets/{targetId}/heartbeat")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> heartbeat(@PathVariable UUID projectId, @PathVariable UUID targetId,
            @RequestBody JsonNode request) {
        return driftService.heartbeat(projectId, targetId, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/runtime-targets/{targetId}/heartbeat/latest")
    public Map<String, Object> latestHeartbeat(@PathVariable UUID projectId, @PathVariable UUID targetId) {
        return driftService.latestHeartbeat(projectId, targetId);
    }

    @PostMapping("/api/v1/projects/{projectId}/runtime-targets/{targetId}/deployment-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> deploymentReport(@PathVariable UUID projectId, @PathVariable UUID targetId,
            @RequestBody JsonNode request) {
        return driftService.deploymentReport(projectId, targetId, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/runtime-targets/{targetId}/deployment-reports/latest")
    public Map<String, Object> latestDeploymentReport(@PathVariable UUID projectId, @PathVariable UUID targetId) {
        return driftService.latestDeploymentReport(projectId, targetId);
    }

    @PostMapping("/api/v1/projects/{projectId}/runtime-targets/{targetId}/config-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> configReport(@PathVariable UUID projectId, @PathVariable UUID targetId,
            @RequestBody JsonNode request) {
        return driftService.configReport(projectId, targetId, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/runtime-targets/{targetId}/config-reports/latest")
    public Map<String, Object> latestConfigReport(@PathVariable UUID projectId, @PathVariable UUID targetId) {
        return driftService.latestConfigReport(projectId, targetId);
    }

    @PostMapping("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/drift/check")
    public Map<String, Object> check(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID environmentId, @RequestBody JsonNode request) {
        return driftService.check(projectId, serviceId, environmentId, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/drift-findings")
    public List<Map<String, Object>> findings(@PathVariable UUID projectId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String driftType,
            @RequestParam(required = false) String severity) {
        return driftService.findings(projectId, serviceId, environmentId, status, driftType, severity);
    }

    @GetMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}")
    public Map<String, Object> finding(@PathVariable UUID projectId, @PathVariable UUID driftFindingId) {
        return driftService.finding(projectId, driftFindingId);
    }

    @PostMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/acknowledge")
    public Map<String, Object> acknowledge(@PathVariable UUID projectId, @PathVariable UUID driftFindingId,
            @RequestBody JsonNode request) {
        return driftService.acknowledge(projectId, driftFindingId, request, false);
    }

    @PostMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/resolve")
    public Map<String, Object> resolve(@PathVariable UUID projectId, @PathVariable UUID driftFindingId,
            @RequestBody JsonNode request) {
        return driftService.resolve(projectId, driftFindingId, request);
    }

    @PostMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/repair-intents")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> repairIntent(@PathVariable UUID projectId, @PathVariable UUID driftFindingId,
            @RequestBody JsonNode request) {
        return driftService.repairIntent(projectId, driftFindingId, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/repair-intents")
    public List<Map<String, Object>> repairIntents(@PathVariable UUID projectId, @PathVariable UUID driftFindingId) {
        return driftService.repairIntents(projectId, driftFindingId);
    }

    @GetMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/evidence")
    public Map<String, Object> evidence(@PathVariable UUID projectId, @PathVariable UUID driftFindingId) {
        return driftService.evidence(projectId, driftFindingId);
    }

    @PostMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/acknowledge-manual-change")
    public Map<String, Object> acknowledgeManualChange(@PathVariable UUID projectId, @PathVariable UUID driftFindingId,
            @RequestBody JsonNode request) {
        return driftService.acknowledge(projectId, driftFindingId, request, true);
    }

    @PostMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/accept-actual-as-desired")
    public Map<String, Object> acceptActualAsDesired(@PathVariable UUID projectId, @PathVariable UUID driftFindingId,
            @RequestBody JsonNode request) {
        return driftService.acceptActualAsDesired(projectId, driftFindingId, request);
    }

    @PostMapping("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/recommend-redeploy-desired")
    public Map<String, Object> recommendRedeploy(@PathVariable UUID projectId, @PathVariable UUID driftFindingId,
            @RequestBody JsonNode request) {
        return driftService.recommendRedeploy(projectId, driftFindingId, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/actual-state")
    public Map<String, Object> actualState(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID environmentId) {
        return driftService.actualState(projectId, serviceId, environmentId);
    }
}
