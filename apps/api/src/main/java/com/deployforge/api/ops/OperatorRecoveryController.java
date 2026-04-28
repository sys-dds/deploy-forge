package com.deployforge.api.ops;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/ops")
public class OperatorRecoveryController {
    private final OperatorRecoveryService recoveryService;

    public OperatorRecoveryController(OperatorRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @GetMapping("/stuck-commands")
    public List<Map<String, Object>> stuckCommands(@PathVariable UUID projectId) {
        return recoveryService.stuckCommands(projectId);
    }

    @GetMapping("/stuck-rollouts")
    public List<Map<String, Object>> stuckRollouts(@PathVariable UUID projectId) {
        return recoveryService.stuckRollouts(projectId);
    }

    @GetMapping("/stuck-rollbacks")
    public List<Map<String, Object>> stuckRollbacks(@PathVariable UUID projectId) {
        return recoveryService.stuckRollbacks(projectId);
    }

    @GetMapping("/stale-leases")
    public List<Map<String, Object>> staleLeases(@PathVariable UUID projectId) {
        return recoveryService.staleLeases(projectId);
    }

    @GetMapping("/stale-locks")
    public List<Map<String, Object>> staleLocks(@PathVariable UUID projectId) {
        return recoveryService.staleLocks(projectId);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@PathVariable UUID projectId) {
        return recoveryService.summary(projectId);
    }

    @GetMapping("/investigate")
    public Map<String, Object> investigate(@PathVariable UUID projectId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) UUID environmentId,
            @RequestParam(required = false) String commandStatus,
            @RequestParam(required = false) String driftStatus,
            @RequestParam(required = false) String repairPlanStatus) {
        return recoveryService.investigate(projectId, serviceId, environmentId, commandStatus, driftStatus, repairPlanStatus);
    }

    @GetMapping("/recovery-evidence")
    public Map<String, Object> recoveryEvidence(@PathVariable UUID projectId) {
        return recoveryService.recoveryEvidence(projectId);
    }

    @GetMapping("/recovery-actions")
    public List<Map<String, Object>> recoveryActions(@PathVariable UUID projectId) {
        return recoveryService.recoveryEvents(projectId);
    }

    @PostMapping("/commands/{commandId}/force-park")
    public Map<String, Object> forcePark(@PathVariable UUID projectId, @PathVariable UUID commandId,
            @RequestBody JsonNode request) {
        return recoveryService.forcePark(projectId, commandId, request);
    }

    @PostMapping("/commands/{commandId}/force-retry")
    public Map<String, Object> forceRetry(@PathVariable UUID projectId, @PathVariable UUID commandId,
            @RequestBody JsonNode request) {
        return recoveryService.forceRetry(projectId, commandId, request);
    }

    @PostMapping("/commands/{commandId}/mark-manually-resolved")
    public Map<String, Object> manualResolve(@PathVariable UUID projectId, @PathVariable UUID commandId,
            @RequestBody JsonNode request) {
        return recoveryService.manualResolve(projectId, commandId, request);
    }

    @PostMapping("/leases/{commandId}/force-release-stale")
    public Map<String, Object> forceReleaseStaleLease(@PathVariable UUID projectId, @PathVariable UUID commandId,
            @RequestBody JsonNode request) {
        return recoveryService.forceReleaseStaleLease(projectId, commandId, request);
    }
}
