package com.deployforge.api.rollback;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.gate.EvaluateGatesRequest;
import com.deployforge.api.gate.GateAttemptResponse;
import com.deployforge.api.gate.GateEvidenceResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class RollbackController {
    private final RollbackService rollbackService;

    public RollbackController(RollbackService rollbackService) {
        this.rollbackService = rollbackService;
    }

    @PostMapping("/rollback-recommendations/{recommendationId}/rollback-executions/start")
    public RollbackExecutionResponse start(@PathVariable UUID projectId, @PathVariable UUID recommendationId,
            @Valid @RequestBody StartRollbackRequest request) {
        return rollbackService.start(projectId, recommendationId, request);
    }

    @GetMapping("/rollback-executions/{rollbackExecutionId}")
    public RollbackExecutionResponse get(@PathVariable UUID projectId, @PathVariable UUID rollbackExecutionId) {
        return rollbackService.get(projectId, rollbackExecutionId);
    }

    @PostMapping("/rollback-executions/{rollbackExecutionId}/gates/evaluate")
    public List<GateAttemptResponse> evaluateGates(@PathVariable UUID projectId, @PathVariable UUID rollbackExecutionId,
            @Valid @RequestBody EvaluateGatesRequest request) {
        return rollbackService.evaluateGates(projectId, rollbackExecutionId, request);
    }

    @GetMapping("/rollback-executions/{rollbackExecutionId}/gates/evidence")
    public GateEvidenceResponse gateEvidence(@PathVariable UUID projectId, @PathVariable UUID rollbackExecutionId) {
        return rollbackService.gateEvidence(projectId, rollbackExecutionId);
    }

    @PostMapping("/rollback-executions/{rollbackExecutionId}/complete-success")
    public RollbackExecutionResponse completeSuccess(@PathVariable UUID projectId, @PathVariable UUID rollbackExecutionId,
            @Valid @RequestBody CompleteRollbackRequest request) {
        return rollbackService.completeSuccess(projectId, rollbackExecutionId, request);
    }

    @PostMapping("/rollback-executions/{rollbackExecutionId}/complete-failure")
    public RollbackExecutionResponse completeFailure(@PathVariable UUID projectId, @PathVariable UUID rollbackExecutionId,
            @Valid @RequestBody CompleteRollbackRequest request) {
        return rollbackService.completeFailure(projectId, rollbackExecutionId, request);
    }

    @PostMapping("/rollback-executions/{rollbackExecutionId}/retry")
    public RollbackExecutionResponse retry(@PathVariable UUID projectId, @PathVariable UUID rollbackExecutionId,
            @Valid @RequestBody RetryRollbackRequest request) {
        return rollbackService.retry(projectId, rollbackExecutionId, request);
    }

    @GetMapping("/rollback-executions/{rollbackExecutionId}/evidence")
    public RollbackEvidenceResponse evidence(@PathVariable UUID projectId, @PathVariable UUID rollbackExecutionId) {
        return rollbackService.evidence(projectId, rollbackExecutionId);
    }

    @GetMapping("/rollouts/{rolloutId}/recovery")
    public FailedRolloutRecoveryResponse recovery(@PathVariable UUID projectId, @PathVariable UUID rolloutId) {
        return rollbackService.recovery(projectId, rolloutId);
    }

    @PostMapping("/rollouts/{rolloutId}/mark-manual-intervention")
    public FailedRolloutRecoveryResponse markManual(@PathVariable UUID projectId, @PathVariable UUID rolloutId,
            @Valid @RequestBody MarkManualInterventionRequest request) {
        return rollbackService.markManual(projectId, rolloutId, request);
    }

    @GetMapping("/rollouts/{rolloutId}/recovery-timeline")
    public RecoveryTimelineResponse timeline(@PathVariable UUID projectId, @PathVariable UUID rolloutId) {
        return rollbackService.timeline(projectId, rolloutId);
    }
}
