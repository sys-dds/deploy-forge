package com.deployforge.api.approval;

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
@RequestMapping("/api/v1/projects/{projectId}")
public class ApprovalController {
    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @PostMapping("/deployment-plans/{planId}/approval-requests")
    public ApprovalRequestResponse create(@PathVariable UUID projectId, @PathVariable UUID planId,
            @Valid @RequestBody CreateApprovalRequestRequest request) {
        return service.create(projectId, planId, request);
    }

    @GetMapping("/deployment-plans/{planId}/approval-requests")
    public List<ApprovalRequestResponse> list(@PathVariable UUID projectId, @PathVariable UUID planId) {
        return service.list(projectId, planId);
    }

    @PostMapping("/approval-requests/{approvalRequestId}/decisions")
    public ApprovalDecisionResponse decide(@PathVariable UUID projectId, @PathVariable UUID approvalRequestId,
            @Valid @RequestBody CreateApprovalDecisionRequest request) {
        return service.decide(projectId, approvalRequestId, request);
    }

    @PostMapping("/approval-requests/{approvalRequestId}/expire")
    public ApprovalRequestResponse expire(@PathVariable UUID projectId, @PathVariable UUID approvalRequestId,
            @Valid @RequestBody ExpireApprovalRequestRequest request) {
        return service.expire(projectId, approvalRequestId, request);
    }
}
