package com.deployforge.api.state;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.deployforge.api.environment.EnvironmentRepository;
import com.deployforge.api.environment.EnvironmentResponse;
import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.service.ServiceRepository;
import com.deployforge.api.service.ServiceResponse;
import com.deployforge.api.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state")
public class EnvironmentDeploymentStateController {

    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvironmentDeploymentStateRepository stateRepository;

    public EnvironmentDeploymentStateController(ProjectRepository projectRepository, ServiceRepository serviceRepository,
            EnvironmentRepository environmentRepository, EnvironmentDeploymentStateRepository stateRepository) {
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
        this.environmentRepository = environmentRepository;
        this.stateRepository = stateRepository;
    }

    @GetMapping
    public EnvironmentDeploymentStateResponse get(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID environmentId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
        }
        ServiceResponse service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Service not found"));
        EnvironmentResponse environment = environmentRepository.findById(environmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment not found"));
        if (!service.projectId().equals(projectId) || !environment.projectId().equals(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ENVIRONMENT_NOT_FOUND", "Environment state not found");
        }
        return stateRepository.find(serviceId, environmentId)
                .orElseGet(() -> new EnvironmentDeploymentStateResponse(
                        projectId,
                        serviceId,
                        environmentId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "NEVER_DEPLOYED",
                        OffsetDateTime.now()
                ));
    }
}
