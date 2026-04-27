package com.deployforge.api.rollout;

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
public class RolloutController {
    private final RolloutService rolloutService;

    public RolloutController(RolloutService rolloutService) {
        this.rolloutService = rolloutService;
    }

    @PostMapping("/deployment-plans/{planId}/rollouts/start")
    public RolloutExecutionResponse start(@PathVariable UUID projectId, @PathVariable UUID planId,
            @Valid @RequestBody StartRolloutRequest request) {
        return rolloutService.start(projectId, planId, request);
    }

    @GetMapping("/deployment-plans/{planId}/rollout")
    public RolloutExecutionResponse getByPlan(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return rolloutService.getByPlan(projectId, planId);
    }

    @GetMapping("/rollouts/{rolloutId}")
    public RolloutExecutionResponse get(@PathVariable UUID projectId, @PathVariable UUID rolloutId) {
        return rolloutService.get(projectId, rolloutId);
    }

    @GetMapping("/rollouts/{rolloutId}/waves")
    public List<RolloutWaveResponse> waves(@PathVariable UUID projectId, @PathVariable UUID rolloutId) {
        return rolloutService.waves(projectId, rolloutId);
    }

    @PostMapping("/rollouts/{rolloutId}/waves/{waveNumber}/gates/evaluate")
    public List<GateAttemptResponse> evaluateWaveGates(@PathVariable UUID projectId, @PathVariable UUID rolloutId,
            @PathVariable int waveNumber, @Valid @RequestBody EvaluateGatesRequest request) {
        return rolloutService.evaluateWaveGates(projectId, rolloutId, waveNumber, request);
    }

    @GetMapping("/rollouts/{rolloutId}/waves/{waveNumber}/gates/evidence")
    public GateEvidenceResponse waveGateEvidence(@PathVariable UUID projectId, @PathVariable UUID rolloutId,
            @PathVariable int waveNumber) {
        return rolloutService.waveEvidence(projectId, rolloutId, waveNumber);
    }

    @PostMapping("/rollouts/{rolloutId}/advance")
    public RolloutExecutionResponse advance(@PathVariable UUID projectId, @PathVariable UUID rolloutId,
            @Valid @RequestBody RolloutActionRequest request) {
        return rolloutService.advance(projectId, rolloutId, request);
    }

    @PostMapping("/rollouts/{rolloutId}/pause")
    public RolloutExecutionResponse pause(@PathVariable UUID projectId, @PathVariable UUID rolloutId,
            @Valid @RequestBody RolloutActionRequest request) {
        return rolloutService.pause(projectId, rolloutId, request);
    }

    @PostMapping("/rollouts/{rolloutId}/resume")
    public RolloutExecutionResponse resume(@PathVariable UUID projectId, @PathVariable UUID rolloutId,
            @Valid @RequestBody RolloutActionRequest request) {
        return rolloutService.resume(projectId, rolloutId, request);
    }

    @PostMapping("/rollouts/{rolloutId}/abort")
    public RolloutExecutionResponse abort(@PathVariable UUID projectId, @PathVariable UUID rolloutId,
            @Valid @RequestBody RolloutActionRequest request) {
        return rolloutService.abort(projectId, rolloutId, request);
    }

    @GetMapping("/rollouts/{rolloutId}/evidence")
    public RolloutEvidenceResponse evidence(@PathVariable UUID projectId, @PathVariable UUID rolloutId) {
        return rolloutService.evidence(projectId, rolloutId);
    }

    @GetMapping("/rollouts/{rolloutId}/rollback-recommendation")
    public RollbackRecommendationResponse recommendation(@PathVariable UUID projectId, @PathVariable UUID rolloutId) {
        return rolloutService.recommendation(projectId, rolloutId);
    }

    @PostMapping("/rollback-recommendations/{recommendationId}/acknowledge")
    public RollbackRecommendationResponse acknowledge(@PathVariable UUID projectId, @PathVariable UUID recommendationId,
            @Valid @RequestBody AcknowledgeRollbackRecommendationRequest request) {
        return rolloutService.acknowledgeRecommendation(projectId, recommendationId, request);
    }
}
