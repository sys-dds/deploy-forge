package com.deployforge.api.command;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeploymentCommandController {
    private final DeploymentCommandService commandService;

    public DeploymentCommandController(DeploymentCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/api/v1/projects/{projectId}/commands")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable UUID projectId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode request) {
        return commandService.create(projectId, idempotencyKey, request);
    }

    @GetMapping("/api/v1/projects/{projectId}/commands/{commandId}")
    public Map<String, Object> get(@PathVariable UUID projectId, @PathVariable UUID commandId) {
        return commandService.get(projectId, commandId);
    }

    @GetMapping("/api/v1/projects/{projectId}/commands/backlog")
    public List<Map<String, Object>> backlog(@PathVariable UUID projectId) {
        return commandService.backlog(projectId);
    }

    @GetMapping("/api/v1/projects/{projectId}/commands/parked")
    public List<Map<String, Object>> parked(@PathVariable UUID projectId) {
        return commandService.parked(projectId);
    }

    @PostMapping("/api/v1/projects/{projectId}/commands/{commandId}/requeue")
    public Map<String, Object> requeue(@PathVariable UUID projectId, @PathVariable UUID commandId,
            @RequestBody JsonNode request) {
        return commandService.requeue(projectId, commandId, request);
    }
}
