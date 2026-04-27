package com.deployforge.api.environment;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.shared.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/environments")
public class EnvironmentController {

    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;

    public EnvironmentController(ProjectRepository projectRepository, EnvironmentRepository environmentRepository) {
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse create(@PathVariable UUID projectId, @Valid @RequestBody CreateEnvironmentRequest request) {
        requireProject(projectId);
        EnvironmentType environmentType = parseEnvironmentType(request.environmentType());
        return environmentRepository.create(projectId, environmentType, request);
    }

    @GetMapping
    public List<EnvironmentResponse> list(@PathVariable UUID projectId) {
        requireProject(projectId);
        return environmentRepository.findByProjectId(projectId);
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
        }
    }

    private EnvironmentType parseEnvironmentType(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "environmentType is required");
        }
        try {
            return EnvironmentType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "environmentType must be DEV, QA, STAGING, or PROD");
        }
    }
}
