package com.deployforge.api.runner;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class RunnerController {
    private final RunnerService runnerService;

    public RunnerController(RunnerService runnerService) {
        this.runnerService = runnerService;
    }

    @PostMapping("/runners/register")
    public Map<String, Object> register(@PathVariable UUID projectId, @RequestBody JsonNode request) {
        return runnerService.register(projectId, request);
    }

    @PostMapping("/runners/{nodeId}/heartbeat")
    public Map<String, Object> heartbeat(@PathVariable UUID projectId, @PathVariable String nodeId) {
        return runnerService.heartbeat(projectId, nodeId);
    }

    @GetMapping("/runners")
    public List<Map<String, Object>> runners(@PathVariable UUID projectId) {
        return runnerService.runners(projectId);
    }

    @PostMapping("/runners/{nodeId}/commands/claim")
    public Map<String, Object> claim(@PathVariable UUID projectId, @PathVariable String nodeId, @RequestBody JsonNode request) {
        return runnerService.claim(projectId, nodeId, request);
    }

    @PostMapping("/runners/{nodeId}/commands/{commandId}/complete-success")
    public Map<String, Object> completeSuccess(@PathVariable UUID projectId, @PathVariable String nodeId,
            @PathVariable UUID commandId, @RequestBody JsonNode request) {
        return runnerService.completeSuccess(projectId, nodeId, commandId, request);
    }

    @PostMapping("/runners/{nodeId}/commands/{commandId}/complete-failure")
    public Map<String, Object> completeFailure(@PathVariable UUID projectId, @PathVariable String nodeId,
            @PathVariable UUID commandId, @RequestBody JsonNode request) {
        return runnerService.completeFailure(projectId, nodeId, commandId, request);
    }

    @PostMapping("/runners/{nodeId}/tick")
    public Map<String, Object> tick(@PathVariable UUID projectId, @PathVariable String nodeId, @RequestBody JsonNode request) {
        return runnerService.tick(projectId, nodeId, request);
    }
}
