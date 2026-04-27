package com.deployforge.api.readiness;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.deployforge.api.approval.ApprovalRepository;
import com.deployforge.api.artifact.ArtifactDeployabilityResponse;
import com.deployforge.api.artifact.ReleaseArtifactRepository;
import com.deployforge.api.artifact.ReleaseArtifactResponse;
import com.deployforge.api.artifact.ReleaseArtifactService;
import com.deployforge.api.environment.EnvironmentRepository;
import com.deployforge.api.environment.EnvironmentResponse;
import com.deployforge.api.gate.GateExecutionService;
import com.deployforge.api.lock.DeploymentLockRepository;
import com.deployforge.api.override.DeploymentOverrideRepository;
import com.deployforge.api.override.DeploymentOverrideType;
import com.deployforge.api.plan.DeploymentPlanResponse;
import com.deployforge.api.plan.DeploymentPlanService;
import com.deployforge.api.policy.ProtectedEnvironmentPolicyRepository;
import com.deployforge.api.promotion.PromotionEvidenceRepository;
import com.deployforge.api.promotion.PromotionRuleRepository;
import com.deployforge.api.project.ProjectRepository;
import com.deployforge.api.project.ProjectResponse;
import com.deployforge.api.service.ServiceRepository;
import com.deployforge.api.service.ServiceResponse;
import org.springframework.stereotype.Service;

@Service
public class DeploymentReadinessService {
    private final DeploymentPlanService planService;
    private final ProjectRepository projectRepository;
    private final ServiceRepository serviceRepository;
    private final EnvironmentRepository environmentRepository;
    private final ReleaseArtifactRepository artifactRepository;
    private final ReleaseArtifactService artifactService;
    private final ProtectedEnvironmentPolicyRepository policyRepository;
    private final PromotionRuleRepository promotionRuleRepository;
    private final PromotionEvidenceRepository promotionEvidenceRepository;
    private final ApprovalRepository approvalRepository;
    private final DeploymentLockRepository lockRepository;
    private final DeploymentOverrideRepository overrideRepository;
    private final GateExecutionService gateExecutionService;

    public DeploymentReadinessService(DeploymentPlanService planService, ProjectRepository projectRepository,
            ServiceRepository serviceRepository, EnvironmentRepository environmentRepository,
            ReleaseArtifactRepository artifactRepository, ReleaseArtifactService artifactService,
            ProtectedEnvironmentPolicyRepository policyRepository, PromotionRuleRepository promotionRuleRepository,
            PromotionEvidenceRepository promotionEvidenceRepository, ApprovalRepository approvalRepository,
            DeploymentLockRepository lockRepository, DeploymentOverrideRepository overrideRepository,
            GateExecutionService gateExecutionService) {
        this.planService = planService;
        this.projectRepository = projectRepository;
        this.serviceRepository = serviceRepository;
        this.environmentRepository = environmentRepository;
        this.artifactRepository = artifactRepository;
        this.artifactService = artifactService;
        this.policyRepository = policyRepository;
        this.promotionRuleRepository = promotionRuleRepository;
        this.promotionEvidenceRepository = promotionEvidenceRepository;
        this.approvalRepository = approvalRepository;
        this.lockRepository = lockRepository;
        this.overrideRepository = overrideRepository;
        this.gateExecutionService = gateExecutionService;
    }

    public DeploymentReadinessResponse get(UUID projectId, UUID planId) {
        DeploymentPlanResponse plan = planService.get(projectId, planId);
        ProjectResponse project = projectRepository.findById(projectId).orElseThrow();
        ServiceResponse service = serviceRepository.findById(plan.serviceId()).orElseThrow();
        EnvironmentResponse environment = environmentRepository.findById(plan.targetEnvironmentId()).orElseThrow();
        ReleaseArtifactResponse artifact = artifactRepository.findById(plan.artifactId()).orElseThrow();
        List<DeploymentReadinessCheckResponse> checks = new ArrayList<>();

        ArtifactDeployabilityResponse deployability = artifactService.evaluate(project, service, artifact, environment);
        add(checks, "ARTIFACT_DEPLOYABLE", deployability.deployable(), "Artifact is deployable", "Artifact is not deployable");

        var policy = policyRepository.find(projectId, plan.targetEnvironmentId());
        var rule = promotionRuleRepository.find(projectId, plan.targetEnvironmentId());
        boolean promotionRequired = policy.map(p -> p.requirePromotionEvidence()).orElse(false)
                || rule.map(r -> r.requiresSuccessfulSourceDeployment() && r.requiredSourceEnvironmentId() != null).orElse(false);
        boolean promotionOk = !promotionRequired || overrideRepository.exists(projectId, planId, DeploymentOverrideType.PROMOTION_EVIDENCE)
                || rule.map(r -> r.requiredSourceEnvironmentId() == null
                        || promotionEvidenceRepository.existsForPromotion(projectId, plan.serviceId(), plan.artifactId(),
                        r.requiredSourceEnvironmentId(), plan.targetEnvironmentId())).orElse(false);
        add(checks, "PROMOTION_EVIDENCE_PRESENT", promotionOk, "Required source promotion evidence is present",
                "Required source promotion evidence is missing");

        boolean approvalRequired = policy.map(p -> p.requireApproval()).orElse(environment.requiresApproval());
        boolean approvalOk = !approvalRequired || overrideRepository.exists(projectId, planId, DeploymentOverrideType.APPROVAL)
                || approvalRepository.hasApprovedForPlan(projectId, planId);
        if (approvalRepository.hasRejectedOrExpiredForPlan(projectId, planId)) {
            approvalOk = false;
        }
        add(checks, "APPROVAL_GRANTED", approvalOk, "Required approval is granted", "Required approval is missing, rejected, or expired");

        var gateEvidence = gateExecutionService.evidence(projectId, planId);
        boolean gatesOk = gateEvidence.attempts().isEmpty() || gateEvidence.requiredGatesPassed()
                || overrideRepository.exists(projectId, planId, DeploymentOverrideType.GATE);
        add(checks, "REQUIRED_GATES_PASSED", gatesOk, "Required gates passed", "Required gates have not passed");

        boolean lockOk = lockRepository.activeForPlan(projectId, planId);
        add(checks, "DEPLOYMENT_LOCK_ACTIVE", lockOk, "Deployment lock is active for this plan", "Deployment lock must be acquired");

        boolean ready = checks.stream().allMatch(check -> check.status().equals("PASS"));
        String action = recommended(checks);
        return new DeploymentReadinessResponse(planId, ready, checks, ready ? "READY" : action);
    }

    private void add(List<DeploymentReadinessCheckResponse> checks, String code, boolean pass, String passMessage, String failMessage) {
        checks.add(new DeploymentReadinessCheckResponse(code, pass ? "PASS" : "FAIL", pass ? passMessage : failMessage));
    }

    private String recommended(List<DeploymentReadinessCheckResponse> checks) {
        for (DeploymentReadinessCheckResponse check : checks) {
            if (check.status().equals("FAIL")) {
                return switch (check.code()) {
                    case "APPROVAL_GRANTED" -> "REQUEST_APPROVAL";
                    case "PROMOTION_EVIDENCE_PRESENT" -> "ADD_PROMOTION_EVIDENCE";
                    case "REQUIRED_GATES_PASSED" -> "RUN_GATES";
                    case "DEPLOYMENT_LOCK_ACTIVE" -> "ACQUIRE_LOCK";
                    default -> "READY";
                };
            }
        }
        return "READY";
    }
}
