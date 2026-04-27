package com.deployforge.api.service;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/services")
public class ServiceController {

    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;

    public ServiceController(ProjectRepository projectRepository, ServiceRepository serviceRepository) {
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(@PathVariable UUID projectId, @RequestBody CreateServiceRequest request) {
        requireProject(projectId);
        requireText(request.name(), "name");
        requireText(request.slug(), "slug");
        return serviceRepository.create(projectId, request);
    }

    @GetMapping
    public List<ServiceResponse> list(@PathVariable UUID projectId) {
        requireProject(projectId);
        return serviceRepository.findByProjectId(projectId);
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Project not found");
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " is required");
        }
    }
}
