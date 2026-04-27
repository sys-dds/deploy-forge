package com.deployforge.api.approval;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.plan.DeploymentPlanResponse;
import com.deployforge.api.plan.DeploymentPlanService;
import com.deployforge.api.plan.DeploymentPlanStatus;
import com.deployforge.api.policy.ProtectedEnvironmentPolicyRepository;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ApprovalService {
    private final DeploymentPlanService planService;
    private final ProtectedEnvironmentPolicyRepository policyRepository;
    private final ApprovalRepository approvalRepository;
    private final DeploymentIntentEventRepository eventRepository;

    public ApprovalService(DeploymentPlanService planService, ProtectedEnvironmentPolicyRepository policyRepository,
            ApprovalRepository approvalRepository, DeploymentIntentEventRepository eventRepository) {
        this.planService = planService;
        this.policyRepository = policyRepository;
        this.approvalRepository = approvalRepository;
        this.eventRepository = eventRepository;
    }

    public ApprovalRequestResponse create(UUID projectId, UUID planId, CreateApprovalRequestRequest request) {
        DeploymentPlanResponse plan = planService.get(projectId, planId);
        if (plan.status() == DeploymentPlanStatus.CANCELLED || plan.status() == DeploymentPlanStatus.ABORTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPROVAL_REQUEST_BLOCKED", "Cancelled or aborted plans cannot request approval");
        }
        int requestedCount = request.requiredApprovalCount() == null ? 1 : request.requiredApprovalCount();
        int policyCount = policyRepository.find(projectId, plan.targetEnvironmentId())
                .map(policy -> policy.requireApproval() ? policy.requiredApprovalCount() : 1)
                .orElse(1);
        if (requestedCount < policyCount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPROVAL_COUNT_TOO_LOW", "Required approval count is below protected policy requirement");
        }
        ApprovalRequestResponse response = approvalRepository.findPendingByPlan(planId)
                .orElseGet(() -> approvalRepository.create(projectId, planId, plan.targetEnvironmentId(), request, requestedCount));
        eventRepository.record(projectId, planId, plan.serviceId(), plan.targetEnvironmentId(), plan.artifactId(),
                DeploymentIntentEventType.APPROVAL_REQUESTED, request.requestedBy(), request.reason(), Jsonb.object());
        return response;
    }

    public List<ApprovalRequestResponse> list(UUID projectId, UUID planId) {
        planService.get(projectId, planId);
        return approvalRepository.listByPlan(projectId, planId);
    }

    public ApprovalDecisionResponse decide(UUID projectId, UUID approvalRequestId, CreateApprovalDecisionRequest request) {
        ApprovalRequestResponse approval = getRequest(projectId, approvalRequestId);
        if (approval.status() != ApprovalStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "APPROVAL_FINAL", "Only pending approval requests can receive decisions");
        }
        try {
            ApprovalDecisionResponse decision = approvalRepository.addDecision(approvalRequestId, request);
            ApprovalRequestResponse updated = request.decision() == ApprovalDecision.APPROVE
                    ? approvalRepository.approve(approval)
                    : approvalRepository.mark(approval.id(), ApprovalStatus.REJECTED);
            DeploymentIntentEventType eventType = request.decision() == ApprovalDecision.APPROVE
                    ? DeploymentIntentEventType.APPROVAL_APPROVED : DeploymentIntentEventType.APPROVAL_REJECTED;
            eventRepository.record(projectId, approval.deploymentPlanId(), null, approval.environmentId(), null,
                    eventType, request.decidedBy(), request.reason(), Jsonb.object().put("approvalStatus", updated.status().name()));
            return decision;
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "APPROVER_ALREADY_DECIDED", "Approver has already decided this approval request");
        }
    }

    public ApprovalRequestResponse expire(UUID projectId, UUID approvalRequestId, ExpireApprovalRequestRequest request) {
        ApprovalRequestResponse approval = getRequest(projectId, approvalRequestId);
        if (approval.status() != ApprovalStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "APPROVAL_FINAL", "Only pending approval requests can expire");
        }
        if (approval.expiresAt() != null && approval.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "APPROVAL_NOT_EXPIRED", "Approval request has not reached its expiry time");
        }
        ApprovalRequestResponse expired = approvalRepository.mark(approval.id(), ApprovalStatus.EXPIRED);
        eventRepository.record(projectId, approval.deploymentPlanId(), null, approval.environmentId(), null,
                DeploymentIntentEventType.APPROVAL_EXPIRED, request.actor(), request.reason(), Jsonb.object());
        return expired;
    }

    private ApprovalRequestResponse getRequest(UUID projectId, UUID approvalRequestId) {
        return approvalRepository.findRequest(projectId, approvalRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPROVAL_REQUEST_NOT_FOUND", "Approval request not found"));
    }
}
