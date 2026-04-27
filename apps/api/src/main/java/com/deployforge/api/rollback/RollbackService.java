package com.deployforge.api.rollback;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventResponse;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.drift.DesiredEnvironmentStateService;
import com.deployforge.api.gate.EvaluateGatesRequest;
import com.deployforge.api.gate.GateAttemptResponse;
import com.deployforge.api.gate.GateEvidenceResponse;
import com.deployforge.api.lock.DeploymentLockRepository;
import com.deployforge.api.lock.DeploymentLockStatus;
import com.deployforge.api.rollout.RollbackRecommendationRepository;
import com.deployforge.api.rollout.RollbackRecommendationResponse;
import com.deployforge.api.rollout.RollbackRecommendationStatus;
import com.deployforge.api.rollout.RolloutExecutionResponse;
import com.deployforge.api.rollout.RolloutService;
import com.deployforge.api.rollout.RolloutStatus;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.deployforge.api.state.EnvironmentDeploymentStateRepository;
import com.deployforge.api.state.EnvironmentDeploymentStateResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackService {
    private final RolloutService rolloutService;
    private final RollbackRecommendationRepository recommendationRepository;
    private final RollbackRepository rollbackRepository;
    private final com.deployforge.api.gate.GateExecutionService gateExecutionService;
    private final EnvironmentDeploymentStateRepository stateRepository;
    private final DeploymentLockRepository lockRepository;
    private final DeploymentIntentEventRepository eventRepository;
    private final DesiredEnvironmentStateService desiredStateService;

    public RollbackService(RolloutService rolloutService, RollbackRecommendationRepository recommendationRepository,
            RollbackRepository rollbackRepository, com.deployforge.api.gate.GateExecutionService gateExecutionService,
            EnvironmentDeploymentStateRepository stateRepository, DeploymentLockRepository lockRepository,
            DeploymentIntentEventRepository eventRepository, DesiredEnvironmentStateService desiredStateService) {
        this.rolloutService = rolloutService;
        this.recommendationRepository = recommendationRepository;
        this.rollbackRepository = rollbackRepository;
        this.gateExecutionService = gateExecutionService;
        this.stateRepository = stateRepository;
        this.lockRepository = lockRepository;
        this.eventRepository = eventRepository;
        this.desiredStateService = desiredStateService;
    }

    @Transactional
    public RollbackExecutionResponse start(UUID projectId, UUID recommendationId, StartRollbackRequest request) {
        RollbackRecommendationResponse recommendation = recommendation(projectId, recommendationId);
        if (recommendation.recommendationStatus() != RollbackRecommendationStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_RECOMMENDATION_NOT_OPEN", "Only open recommendations can start rollback");
        }
        RolloutExecutionResponse rollout = rolloutService.get(projectId, recommendation.rolloutExecutionId());
        if (rollout.status() != RolloutStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_ROLLOUT_NOT_FAILED", "Rollback can only start for failed rollouts");
        }
        return rollbackRepository.findByRecommendation(projectId, recommendationId).map(existing -> {
            if (Objects.equals(existing.startedBy(), request.startedBy()) && Objects.equals(existing.reason(), request.reason())) {
                return existing;
            }
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_ALREADY_STARTED", "Rollback execution already exists");
        }).orElseGet(() -> {
            RollbackStatus status = recommendation.recommendedArtifactId() == null
                    ? RollbackStatus.MANUAL_INTERVENTION_REQUIRED : RollbackStatus.RUNNING;
            RollbackExecutionResponse rollback = rollbackRepository.create(recommendation, request.startedBy(), request.reason(), status);
            if (status == RollbackStatus.RUNNING) {
                stateRepository.markRollbackRunning(projectId, recommendation.serviceId(), recommendation.environmentId(),
                        recommendation.deploymentPlanId(), recommendation.rolloutExecutionId(), rollback.id());
                eventRepository.record(projectId, recommendation.deploymentPlanId(), recommendation.serviceId(), recommendation.environmentId(),
                        recommendation.failedArtifactId(), DeploymentIntentEventType.ROLLBACK_STARTED, request.startedBy(), request.reason(),
                        Jsonb.object().put("rollbackExecutionId", rollback.id().toString()));
            } else {
                stateRepository.markManualIntervention(projectId, recommendation.serviceId(), recommendation.environmentId(),
                        recommendation.deploymentPlanId(), recommendation.rolloutExecutionId(), rollback.id());
                eventRepository.record(projectId, recommendation.deploymentPlanId(), recommendation.serviceId(), recommendation.environmentId(),
                        recommendation.failedArtifactId(), DeploymentIntentEventType.ROLLBACK_MANUAL_INTERVENTION_REQUIRED,
                        request.startedBy(), "No safe rollback target artifact exists",
                        Jsonb.object().put("rollbackExecutionId", rollback.id().toString()));
            }
            return rollback;
        });
    }

    public RollbackExecutionResponse get(UUID projectId, UUID rollbackId) {
        return rollbackRepository.find(projectId, rollbackId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLLBACK_EXECUTION_NOT_FOUND", "Rollback execution not found"));
    }

    @Transactional
    public List<GateAttemptResponse> evaluateGates(UUID projectId, UUID rollbackId, EvaluateGatesRequest request) {
        RollbackExecutionResponse rollback = requireRunnable(get(projectId, rollbackId));
        List<GateAttemptResponse> attempts = gateExecutionService.evaluateForRollback(projectId, rollback.deploymentPlanId(), rollback.id(), request);
        eventRepository.record(projectId, rollback.deploymentPlanId(), rollback.serviceId(), rollback.environmentId(), rollback.failedArtifactId(),
                DeploymentIntentEventType.ROLLBACK_GATE_EVALUATED, request.requestedBy(), "Rollback gates evaluated",
                Jsonb.object().put("rollbackExecutionId", rollback.id().toString()));
        return attempts;
    }

    public GateEvidenceResponse gateEvidence(UUID projectId, UUID rollbackId) {
        RollbackExecutionResponse rollback = get(projectId, rollbackId);
        return gateExecutionService.rollbackEvidence(projectId, rollback.deploymentPlanId(), rollback.id());
    }

    @Transactional
    public RollbackExecutionResponse completeSuccess(UUID projectId, UUID rollbackId, CompleteRollbackRequest request) {
        RollbackExecutionResponse rollback = get(projectId, rollbackId);
        if (rollback.status() == RollbackStatus.SUCCEEDED) {
            if (Objects.equals(rollback.successActor(), request.actor()) && Objects.equals(rollback.successReason(), request.reason())) {
                return rollback;
            }
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_ALREADY_SUCCEEDED", "Rollback already succeeded");
        }
        rollback = requireRunnable(rollback);
        if (rollback.targetArtifactId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_TARGET_MISSING", "Rollback has no target artifact");
        }
        GateEvidenceResponse evidence = gateEvidence(projectId, rollbackId);
        if (!evidence.requiredGatesPassed()) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_GATES_NOT_PASSED", "Rollback required gates must pass");
        }
        stateRepository.markRolledBack(projectId, rollback.serviceId(), rollback.environmentId(), rollback.targetArtifactId(),
                rollback.deploymentPlanId(), rollback.rolloutExecutionId(), rollback.id());
        releaseLock(rollback, request.actor(), "Rollback succeeded");
        RollbackExecutionResponse succeeded = rollbackRepository.markSucceeded(rollbackId, request.actor(), request.reason());
        recommendationRepository.supersede(rollback.rollbackRecommendationId(), request.actor(), "Rollback succeeded");
        eventRepository.record(projectId, rollback.deploymentPlanId(), rollback.serviceId(), rollback.environmentId(), rollback.targetArtifactId(),
                DeploymentIntentEventType.ROLLBACK_SUCCEEDED, request.actor(), request.reason(),
                Jsonb.object().put("rollbackExecutionId", rollback.id().toString()));
        eventRepository.record(projectId, rollback.deploymentPlanId(), rollback.serviceId(), rollback.environmentId(), rollback.targetArtifactId(),
                DeploymentIntentEventType.ENVIRONMENT_STATE_UPDATED, request.actor(), "Environment rolled back",
                Jsonb.object().put("stateStatus", "ROLLED_BACK"));
        desiredStateService.recordRollbackSuccess(projectId, rollback.serviceId(), rollback.environmentId(), rollback.targetArtifactId(),
                rollback.deploymentPlanId(), rollback.rolloutExecutionId(), rollback.id(), request.actor(), request.reason());
        return succeeded;
    }

    @Transactional
    public RollbackExecutionResponse completeFailure(UUID projectId, UUID rollbackId, CompleteRollbackRequest request) {
        RollbackExecutionResponse rollback = get(projectId, rollbackId);
        if (rollback.status() == RollbackStatus.FAILED) {
            if (Objects.equals(rollback.failureActor(), request.actor()) && Objects.equals(rollback.failureReason(), request.reason())) {
                return rollback;
            }
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_ALREADY_FAILED", "Rollback already failed");
        }
        rollback = requireRunnable(rollback);
        RollbackExecutionResponse failed = rollbackRepository.markFailed(rollbackId, request.actor(), request.reason());
        stateRepository.markManualIntervention(projectId, rollback.serviceId(), rollback.environmentId(), rollback.deploymentPlanId(),
                rollback.rolloutExecutionId(), rollback.id());
        eventRepository.record(projectId, rollback.deploymentPlanId(), rollback.serviceId(), rollback.environmentId(), rollback.failedArtifactId(),
                DeploymentIntentEventType.ROLLBACK_FAILED, request.actor(), request.reason(),
                Jsonb.object().put("rollbackExecutionId", rollback.id().toString()));
        eventRepository.record(projectId, rollback.deploymentPlanId(), rollback.serviceId(), rollback.environmentId(), rollback.failedArtifactId(),
                DeploymentIntentEventType.ROLLBACK_MANUAL_INTERVENTION_REQUIRED, request.actor(), request.reason(),
                Jsonb.object().put("rollbackExecutionId", rollback.id().toString()));
        return failed;
    }

    @Transactional
    public RollbackExecutionResponse retry(UUID projectId, UUID rollbackId, RetryRollbackRequest request) {
        RollbackExecutionResponse rollback = get(projectId, rollbackId);
        if (rollback.status() != RollbackStatus.FAILED && rollback.status() != RollbackStatus.MANUAL_INTERVENTION_REQUIRED) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_RETRY_BLOCKED", "Only failed rollback can retry");
        }
        RollbackExecutionResponse retried = rollbackRepository.retry(rollbackId);
        stateRepository.markRollbackRunning(projectId, rollback.serviceId(), rollback.environmentId(), rollback.deploymentPlanId(),
                rollback.rolloutExecutionId(), rollback.id());
        eventRepository.record(projectId, rollback.deploymentPlanId(), rollback.serviceId(), rollback.environmentId(), rollback.failedArtifactId(),
                DeploymentIntentEventType.ROLLBACK_RETRY_REQUESTED, request.requestedBy(), request.reason(),
                Jsonb.object().put("rollbackExecutionId", rollback.id().toString()).put("retryCount", retried.retryCount()));
        return retried;
    }

    @Transactional
    public FailedRolloutRecoveryResponse markManual(UUID projectId, UUID rolloutId, MarkManualInterventionRequest request) {
        RolloutExecutionResponse rollout = rolloutService.get(projectId, rolloutId);
        if (rollout.status() != RolloutStatus.FAILED && (request.riskAcknowledgement() == null || request.riskAcknowledgement().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MANUAL_INTERVENTION_RISK_ACK_REQUIRED",
                    "Risk acknowledgement is required for non-failed rollouts");
        }
        RollbackExecutionResponse rollback = rollbackRepository.findByRollout(projectId, rolloutId).orElse(null);
        stateRepository.markManualIntervention(projectId, rollout.serviceId(), rollout.environmentId(), rollout.deploymentPlanId(),
                rollout.id(), rollback == null ? null : rollback.id());
        if (rollback != null && rollback.status() != RollbackStatus.MANUAL_INTERVENTION_REQUIRED) {
            rollbackRepository.markManual(rollback.id(), request.reason());
        }
        eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                DeploymentIntentEventType.ROLLBACK_MANUAL_INTERVENTION_REQUIRED, request.actor(), request.reason(),
                Jsonb.object().put("rolloutId", rolloutId.toString()));
        return recovery(projectId, rolloutId);
    }

    public FailedRolloutRecoveryResponse recovery(UUID projectId, UUID rolloutId) {
        RolloutExecutionResponse rollout = rolloutService.get(projectId, rolloutId);
        RollbackRecommendationResponse recommendation = recommendationRepository.find(projectId, rolloutId).orElse(null);
        RollbackExecutionResponse rollback = rollbackRepository.findByRollout(projectId, rolloutId).orElse(null);
        EnvironmentDeploymentStateResponse state = stateRepository.find(rollout.serviceId(), rollout.environmentId()).orElse(null);
        String recoveryStatus = recoveryStatus(rollout, recommendation, rollback, state);
        String action = recommendedAction(rollout, recommendation, rollback);
        return new FailedRolloutRecoveryResponse(rollout.id(), rollout.status().name(), recoveryStatus, action, recommendation,
                rollback, state, List.of(new RecoveryCheckResponse("ROLLBACK_RECOMMENDATION_OPEN",
                        recommendation != null && recommendation.recommendationStatus() == RollbackRecommendationStatus.OPEN ? "PASS" : "INFO",
                        recommendation == null ? "No rollback recommendation exists" : "Rollback recommendation exists")));
    }

    public RollbackEvidenceResponse evidence(UUID projectId, UUID rollbackId) {
        RollbackExecutionResponse rollback = get(projectId, rollbackId);
        EnvironmentDeploymentStateResponse state = stateRepository.find(rollback.serviceId(), rollback.environmentId()).orElse(null);
        return new RollbackEvidenceResponse(rollback.id(), rollback.rollbackRecommendationId(), rollback.rolloutExecutionId(),
                rollback.deploymentPlanId(), rollback.failedArtifactId(), rollback.targetArtifactId(), rollback.status().name(),
                gateEvidence(projectId, rollbackId), state, rollback.status() == RollbackStatus.FAILED ? "RETRY_ROLLBACK" : "NONE",
                rollback.manualInterventionReason(), eventRepository.find(projectId, rollback.deploymentPlanId(), null));
    }

    public RecoveryTimelineResponse timeline(UUID projectId, UUID rolloutId) {
        RolloutExecutionResponse rollout = rolloutService.get(projectId, rolloutId);
        List<DeploymentIntentEventResponse> events = eventRepository.find(projectId, rollout.deploymentPlanId(), null).stream()
                .filter(event -> event.eventType().name().startsWith("ROLLOUT_")
                        || event.eventType().name().startsWith("ROLLBACK_")
                        || event.eventType() == DeploymentIntentEventType.ENVIRONMENT_STATE_UPDATED)
                .toList();
        return new RecoveryTimelineResponse(rolloutId, events);
    }

    private RollbackRecommendationResponse recommendation(UUID projectId, UUID recommendationId) {
        return recommendationRepository.findById(projectId, recommendationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLLBACK_RECOMMENDATION_NOT_FOUND", "Rollback recommendation not found"));
    }

    private RollbackExecutionResponse requireRunnable(RollbackExecutionResponse rollback) {
        if (rollback.status() != RollbackStatus.RUNNING && rollback.status() != RollbackStatus.WAITING_FOR_GATES) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLBACK_NOT_RUNNING", "Rollback execution is not running");
        }
        return rollback;
    }

    private void releaseLock(RollbackExecutionResponse rollback, String actor, String reason) {
        lockRepository.findActiveByPlan(rollback.projectId(), rollback.deploymentPlanId())
                .ifPresent(lock -> lockRepository.mark(lock.id(), DeploymentLockStatus.RELEASED, actor, reason));
    }

    private String recoveryStatus(RolloutExecutionResponse rollout, RollbackRecommendationResponse recommendation,
            RollbackExecutionResponse rollback, EnvironmentDeploymentStateResponse state) {
        if (state != null && "MANUAL_INTERVENTION_REQUIRED".equals(state.stateStatus())) {
            return "MANUAL_INTERVENTION_REQUIRED";
        }
        if (rollback != null && rollback.status() == RollbackStatus.SUCCEEDED) {
            return "ROLLED_BACK";
        }
        if (rollback != null && (rollback.status() == RollbackStatus.RUNNING || rollback.status() == RollbackStatus.WAITING_FOR_GATES)) {
            return "ROLLBACK_RUNNING";
        }
        if (recommendation != null && recommendation.recommendationStatus() == RollbackRecommendationStatus.OPEN) {
            return "ROLLBACK_RECOMMENDED";
        }
        return rollout.status().name();
    }

    private String recommendedAction(RolloutExecutionResponse rollout, RollbackRecommendationResponse recommendation,
            RollbackExecutionResponse rollback) {
        if (rollback != null && rollback.status() == RollbackStatus.SUCCEEDED) {
            return "NONE";
        }
        if (rollback != null && (rollback.status() == RollbackStatus.FAILED || rollback.status() == RollbackStatus.MANUAL_INTERVENTION_REQUIRED)) {
            return "RETRY_ROLLBACK";
        }
        if (rollback != null) {
            return "WAIT";
        }
        if (rollout.status() == RolloutStatus.FAILED && recommendation != null
                && recommendation.recommendationStatus() == RollbackRecommendationStatus.OPEN) {
            return recommendation.recommendedArtifactId() == null ? "MARK_MANUAL_INTERVENTION" : "START_ROLLBACK";
        }
        return "NONE";
    }
}
