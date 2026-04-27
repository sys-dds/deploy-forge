package com.deployforge.api.gate;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.environment.EnvironmentRepository;
import com.deployforge.api.environment.EnvironmentResponse;
import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GateDefinitionService {
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final GateDefinitionRepository repository;
    private final DeploymentIntentEventRepository eventRepository;

    public GateDefinitionService(ProjectRepository projectRepository, EnvironmentRepository environmentRepository,
            GateDefinitionRepository repository, DeploymentIntentEventRepository eventRepository) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.repository = repository;
        this.eventRepository = eventRepository;
    }

    public GateDefinitionResponse create(UUID projectId, CreateGateDefinitionRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
        }
        if (request.environmentId() != null) {
            EnvironmentResponse environment = environmentRepository.findById(request.environmentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found"));
            if (!environment.projectId().equals(projectId)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found");
            }
        }
        validateConfig(request.gateType(), Jsonb.emptyObjectIfNull(request.config()));
        GateDefinitionResponse created = repository.create(projectId, request);
        eventRepository.record(projectId, null, null, request.environmentId(), null,
                DeploymentIntentEventType.GATE_DEFINITION_CREATED, request.createdBy(), "Gate definition created",
                Jsonb.object().put("gateType", request.gateType().name()));
        return created;
    }

    public List<GateDefinitionResponse> list(UUID projectId) {
        return repository.list(projectId);
    }

    public GateDefinitionResponse get(UUID projectId, UUID gateDefinitionId) {
        return repository.find(projectId, gateDefinitionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GATE_DEFINITION_NOT_FOUND", "Gate definition not found"));
    }

    static void validateConfig(GateType type, JsonNode config) {
        switch (type) {
            case HTTP_HEALTH -> {
                require(config, "url");
                require(config, "expectedStatus");
            }
            case SYNTHETIC_CHECK -> require(config, "checkName");
            case METRIC_THRESHOLD -> {
                require(config, "metricName");
                require(config, "operator");
                require(config, "threshold");
                String op = config.get("operator").asText();
                if (!List.of("LESS_THAN", "LESS_THAN_OR_EQUAL", "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "EQUAL").contains(op)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "GATE_CONFIG_INVALID", "Unsupported metric operator");
                }
            }
        }
    }

    private static void require(JsonNode config, String field) {
        if (config == null || !config.hasNonNull(field) || config.get(field).asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GATE_CONFIG_INVALID", "Gate config requires " + field);
        }
    }
}
