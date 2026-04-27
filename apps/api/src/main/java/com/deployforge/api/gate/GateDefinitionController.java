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
@RequestMapping("/api/v1/projects/{projectId}/gate-definitions")
public class GateDefinitionController {
    private final GateDefinitionService service;

    public GateDefinitionController(GateDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    public GateDefinitionResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateGateDefinitionRequest request) {
        return service.create(projectId, request);
    }

    @GetMapping
    public List<GateDefinitionResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }

    @GetMapping("/{gateDefinitionId}")
    public GateDefinitionResponse get(@PathVariable UUID projectId, @PathVariable UUID gateDefinitionId) {
        return service.get(projectId, gateDefinitionId);
    }
}
