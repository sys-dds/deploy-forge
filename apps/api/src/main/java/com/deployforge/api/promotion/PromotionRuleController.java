package com.deployforge.api.promotion;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class PromotionRuleController {
    private final PromotionRuleService service;

    public PromotionRuleController(PromotionRuleService service) {
        this.service = service;
    }

    @PutMapping("/environments/{environmentId}/promotion-rule")
    public PromotionRuleResponse upsert(@PathVariable UUID projectId, @PathVariable UUID environmentId,
            @Valid @RequestBody UpsertPromotionRuleRequest request) {
        return service.upsert(projectId, environmentId, request);
    }

    @GetMapping("/environments/{environmentId}/promotion-rule")
    public PromotionRuleResponse get(@PathVariable UUID projectId, @PathVariable UUID environmentId) {
        return service.get(projectId, environmentId);
    }

    @GetMapping("/promotion-rules")
    public List<PromotionRuleResponse> list(@PathVariable UUID projectId) {
        return service.list(projectId);
    }
}
