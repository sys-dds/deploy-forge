package com.deployforge.api.gate;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class GateExecutionController {
    private final GateExecutionService service;

    public GateExecutionController(GateExecutionService service) {
        this.service = service;
    }

    @PostMapping("/deployment-plans/{planId}/gates/evaluate")
    public List<GateAttemptResponse> evaluate(@PathVariable UUID projectId, @PathVariable UUID planId,
            @Valid @RequestBody EvaluateGatesRequest request) {
        return service.evaluate(projectId, planId, request);
    }

    @GetMapping("/deployment-plans/{planId}/gates/evidence")
    public GateEvidenceResponse evidence(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return service.evidence(projectId, planId);
    }

    @PostMapping("/gate-attempts/{gateAttemptId}/override")
    public GateAttemptResponse override(@PathVariable UUID projectId, @PathVariable UUID gateAttemptId,
            @Valid @RequestBody OverrideGateAttemptRequest request) {
        return service.override(projectId, gateAttemptId, request);
    }

    @PostMapping("/gate-attempts/{gateAttemptId}/rerun")
    public GateAttemptResponse rerun(@PathVariable UUID projectId, @PathVariable UUID gateAttemptId,
            @Valid @RequestBody RerunGateAttemptRequest request) {
        return service.rerun(projectId, gateAttemptId, request);
    }
}
