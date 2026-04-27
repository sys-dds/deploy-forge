package com.deployforge.api.promotion;

import java.util.List;
import java.util.UUID;

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
@RequestMapping("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/promotion-evidence")
public class PromotionEvidenceController {
    private final PromotionEvidenceService service;

    public PromotionEvidenceController(PromotionEvidenceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionEvidenceResponse create(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID artifactId, @Valid @RequestBody CreatePromotionEvidenceRequest request) {
        return service.create(projectId, serviceId, artifactId, request);
    }

    @GetMapping
    public List<PromotionEvidenceResponse> list(@PathVariable UUID projectId, @PathVariable UUID serviceId,
            @PathVariable UUID artifactId) {
        return service.list(projectId, serviceId, artifactId);
    }
}
