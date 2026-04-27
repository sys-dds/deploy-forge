package com.deployforge.api.event;

import java.util.List;
import java.util.UUID;

import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/deployment-intent-events")
public class DeploymentIntentEventController {

    private final ProjectRepository projectRepository;
    private final DeploymentIntentEventRepository eventRepository;

    public DeploymentIntentEventController(ProjectRepository projectRepository,
            DeploymentIntentEventRepository eventRepository) {
        this.projectRepository = projectRepository;
        this.eventRepository = eventRepository;
    }

    @GetMapping
    public List<DeploymentIntentEventResponse> list(@PathVariable UUID projectId,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) DeploymentIntentEventType eventType) {
        if (!projectRepository.existsById(projectId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
        }
        return eventRepository.find(projectId, planId, eventType);
    }
}
