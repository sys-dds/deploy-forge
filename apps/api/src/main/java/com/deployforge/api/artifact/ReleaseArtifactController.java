package com.deployforge.api.artifact;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/services/{serviceId}/artifacts")
public class ReleaseArtifactController {

    private final ReleaseArtifactService artifactService;

    public ReleaseArtifactController(ReleaseArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReleaseArtifactResponse register(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @Valid @RequestBody RegisterReleaseArtifactRequest request) {
        return artifactService.register(projectId, serviceId, request);
    }

    @GetMapping
    public List<ReleaseArtifactResponse> list(@PathVariable UUID projectId, @PathVariable UUID serviceId) {
        return artifactService.list(projectId, serviceId);
    }

    @GetMapping("/{artifactId}")
    public ReleaseArtifactResponse get(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID artifactId) {
        return artifactService.get(projectId, serviceId, artifactId);
    }

    @PostMapping("/{artifactId}/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactEvidenceResponse addEvidence(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID artifactId, @Valid @RequestBody AddArtifactEvidenceRequest request) {
        return artifactService.addEvidence(projectId, serviceId, artifactId, request);
    }

    @GetMapping("/{artifactId}/evidence")
    public List<ArtifactEvidenceResponse> listEvidence(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID artifactId) {
        return artifactService.listEvidence(projectId, serviceId, artifactId);
    }

    @GetMapping("/{artifactId}/deployability")
    public ArtifactDeployabilityResponse deployability(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID artifactId, @RequestParam UUID environmentId) {
        return artifactService.deployability(projectId, serviceId, artifactId, environmentId);
    }
}
