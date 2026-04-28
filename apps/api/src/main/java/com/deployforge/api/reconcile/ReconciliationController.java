package com.deployforge.api.reconcile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ReconciliationController {
    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PutMapping("/reconciliation-policies")
    public Map<String, Object> putPolicy(@PathVariable UUID projectId, @RequestBody JsonNode request) {
        return reconciliationService.putPolicy(projectId, request);
    }

    @PostMapping("/reconciliation-runs")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> run(@PathVariable UUID projectId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode request) {
        return reconciliationService.run(projectId, idempotencyKey, request);
    }

    @GetMapping("/reconciliation-runs/{runId}")
    public Map<String, Object> get(@PathVariable UUID projectId, @PathVariable UUID runId) {
        return reconciliationService.get(projectId, runId);
    }

    @GetMapping("/reconciliation-runs/{runId}/evidence")
    public Map<String, Object> evidence(@PathVariable UUID projectId, @PathVariable UUID runId) {
        return reconciliationService.evidence(projectId, runId);
    }

    @GetMapping("/repair-plans")
    public List<Map<String, Object>> repairPlans(@PathVariable UUID projectId) {
        return reconciliationService.repairPlans(projectId);
    }

    @PostMapping("/repair-plans/{repairPlanId}/approve")
    public Map<String, Object> approve(@PathVariable UUID projectId, @PathVariable UUID repairPlanId,
            @RequestBody JsonNode request) {
        return reconciliationService.approve(projectId, repairPlanId, request);
    }

    @PostMapping("/repair-plans/{repairPlanId}/recommend-execution")
    public Map<String, Object> recommendExecution(@PathVariable UUID projectId, @PathVariable UUID repairPlanId,
            @RequestBody JsonNode request) {
        return reconciliationService.recommendExecution(projectId, repairPlanId, request);
    }
}
