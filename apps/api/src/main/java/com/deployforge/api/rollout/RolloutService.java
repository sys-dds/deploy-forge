package com.deployforge.api.rollout;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.gate.EvaluateGatesRequest;
import com.deployforge.api.gate.GateAttemptResponse;
import com.deployforge.api.gate.GateAttemptStatus;
import com.deployforge.api.gate.GateEvidenceResponse;
import com.deployforge.api.lock.DeploymentLockRepository;
import com.deployforge.api.lock.DeploymentLockStatus;
import com.deployforge.api.plan.DeploymentPlanResponse;
import com.deployforge.api.plan.DeploymentPlanService;
import com.deployforge.api.plan.DeploymentPlanStatus;
import com.deployforge.api.readiness.DeploymentReadinessResponse;
import com.deployforge.api.readiness.DeploymentReadinessService;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.deployforge.api.state.EnvironmentDeploymentStateRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolloutService {
    private final DeploymentPlanService planService;
    private final DeploymentReadinessService readinessService;
    private final DeploymentLockRepository lockRepository;
    private final EnvironmentDeploymentStateRepository stateRepository;
    private final com.deployforge.api.gate.GateExecutionService gateExecutionService;
    private final RolloutRepository rolloutRepository;
    private final RollbackRecommendationRepository recommendationRepository;
    private final DeploymentIntentEventRepository eventRepository;

    public RolloutService(DeploymentPlanService planService, DeploymentReadinessService readinessService,
            DeploymentLockRepository lockRepository, EnvironmentDeploymentStateRepository stateRepository,
            com.deployforge.api.gate.GateExecutionService gateExecutionService, RolloutRepository rolloutRepository,
            RollbackRecommendationRepository recommendationRepository, DeploymentIntentEventRepository eventRepository) {
        this.planService = planService;
        this.readinessService = readinessService;
        this.lockRepository = lockRepository;
        this.stateRepository = stateRepository;
        this.gateExecutionService = gateExecutionService;
        this.rolloutRepository = rolloutRepository;
        this.recommendationRepository = recommendationRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public RolloutExecutionResponse start(UUID projectId, UUID planId, StartRolloutRequest request) {
        DeploymentPlanResponse plan = requireReadyPlan(projectId, planId);
        return rolloutRepository.findByPlan(projectId, planId).map(existing -> {
            if (Objects.equals(existing.startedBy(), request.startedBy()) && Objects.equals(existing.reason(), request.reason())) {
                return existing;
            }
            throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_ALREADY_EXISTS", "Rollout already exists for this deployment plan");
        }).orElseGet(() -> {
            DeploymentReadinessResponse readiness = readinessService.get(projectId, planId);
            if (!readiness.readyToStart()) {
                throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_PREFLIGHT_FAILED", "Deployment readiness must pass before rollout starts");
            }
            if (rolloutRepository.hasActiveForServiceEnvironment(projectId, plan.serviceId(), plan.targetEnvironmentId())) {
                throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_ACTIVE_FOR_TARGET", "Another active rollout already exists for this service and environment");
            }
            RolloutExecutionResponse rollout = rolloutRepository.create(plan, request);
            rolloutRepository.createWaves(rollout);
            stateRepository.markStatus(projectId, plan.serviceId(), plan.targetEnvironmentId(), plan.id(), rollout.id(), "DEPLOYING");
            eventRepository.record(projectId, planId, plan.serviceId(), plan.targetEnvironmentId(), plan.artifactId(),
                    DeploymentIntentEventType.ROLLOUT_STARTED, request.startedBy(), request.reason(), Jsonb.object());
            eventRepository.record(projectId, planId, plan.serviceId(), plan.targetEnvironmentId(), plan.artifactId(),
                    DeploymentIntentEventType.ROLLOUT_WAVE_STARTED, request.startedBy(), "Rollout wave 1 started",
                    Jsonb.object().put("waveNumber", 1));
            return rollout;
        });
    }

    public RolloutExecutionResponse get(UUID projectId, UUID rolloutId) {
        return rolloutRepository.find(projectId, rolloutId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLLOUT_NOT_FOUND", "Rollout not found"));
    }

    public RolloutExecutionResponse getByPlan(UUID projectId, UUID planId) {
        planService.get(projectId, planId);
        return rolloutRepository.findByPlan(projectId, planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLLOUT_NOT_FOUND", "Rollout not found"));
    }

    public List<RolloutWaveResponse> waves(UUID projectId, UUID rolloutId) {
        get(projectId, rolloutId);
        return rolloutRepository.waves(rolloutId);
    }

    @Transactional
    public List<GateAttemptResponse> evaluateWaveGates(UUID projectId, UUID rolloutId, int waveNumber, EvaluateGatesRequest request) {
        RolloutExecutionResponse rollout = requireActive(get(projectId, rolloutId));
        RolloutWaveResponse wave = requireWave(rollout, waveNumber);
        if (wave.status() != RolloutWaveStatus.RUNNING && wave.status() != RolloutWaveStatus.WAITING_FOR_GATES) {
            throw new ApiException(HttpStatus.CONFLICT, "WAVE_GATE_EVALUATION_BLOCKED", "Only running waves can evaluate gates");
        }
        List<GateAttemptResponse> attempts = gateExecutionService.evaluateForWave(projectId, rollout.deploymentPlanId(),
                rollout.id(), wave.id(), request);
        boolean requiredFailed = waveEvidence(projectId, rolloutId, waveNumber).attempts().stream()
                .filter(attempt -> attempt.required())
                .anyMatch(attempt -> attempt.status() == GateAttemptStatus.FAILED || attempt.status() == GateAttemptStatus.TIMED_OUT);
        if (requiredFailed) {
            rolloutRepository.markWave(wave.id(), RolloutWaveStatus.FAILED, "Required wave gate failed");
            RolloutExecutionResponse failed = rolloutRepository.fail(rollout.id(), "Required wave gate failed");
            stateRepository.markStatus(projectId, rollout.serviceId(), rollout.environmentId(), rollout.deploymentPlanId(),
                    rollout.id(), "ROLLBACK_RECOMMENDED");
            createRecommendation(failed, "Required wave gate failed; rollback is recommended");
            eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                    DeploymentIntentEventType.ROLLOUT_WAVE_FAILED, request.requestedBy(), "Required wave gate failed",
                    Jsonb.object().put("waveNumber", waveNumber));
            eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                    DeploymentIntentEventType.ROLLOUT_FAILED, request.requestedBy(), "Required wave gate failed", Jsonb.object());
        }
        return attempts;
    }

    public GateEvidenceResponse waveEvidence(UUID projectId, UUID rolloutId, int waveNumber) {
        RolloutExecutionResponse rollout = get(projectId, rolloutId);
        RolloutWaveResponse wave = requireWave(rollout, waveNumber);
        return gateExecutionService.waveEvidence(projectId, rollout.deploymentPlanId(), wave.id());
    }

    @Transactional
    public RolloutExecutionResponse advance(UUID projectId, UUID rolloutId, RolloutActionRequest request) {
        RolloutExecutionResponse rollout = requireActive(get(projectId, rolloutId));
        RolloutWaveResponse current = requireWave(rollout, rollout.currentWaveNumber());
        GateEvidenceResponse evidence = waveEvidence(projectId, rolloutId, current.waveNumber());
        if (!evidence.requiredGatesPassed()) {
            throw new ApiException(HttpStatus.CONFLICT, "WAVE_GATES_NOT_PASSED", "Current wave required gates must pass before advancing");
        }
        rolloutRepository.markWave(current.id(), RolloutWaveStatus.PASSED, null);
        eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                DeploymentIntentEventType.ROLLOUT_WAVE_COMPLETED, request.actor(), request.reason(),
                Jsonb.object().put("waveNumber", current.waveNumber()));
        List<RolloutWaveResponse> waves = rolloutRepository.waves(rollout.id());
        if (current.waveNumber() >= waves.size()) {
            stateRepository.markDeployed(projectId, rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                    rollout.deploymentPlanId(), rollout.id());
            releaseLock(rollout, request.actor(), "Rollout succeeded");
            RolloutExecutionResponse succeeded = rolloutRepository.mark(rollout.id(), RolloutStatus.SUCCEEDED);
            eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                    DeploymentIntentEventType.ROLLOUT_SUCCEEDED, request.actor(), request.reason(), Jsonb.object());
            return succeeded;
        }
        stateRepository.markStatus(projectId, rollout.serviceId(), rollout.environmentId(), rollout.deploymentPlanId(),
                rollout.id(), "PARTIALLY_DEPLOYED");
        RolloutWaveResponse next = requireWave(rollout, current.waveNumber() + 1);
        rolloutRepository.markWave(next.id(), RolloutWaveStatus.RUNNING, null);
        RolloutExecutionResponse advanced = rolloutRepository.setCurrentWave(rollout.id(), next.waveNumber());
        eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                DeploymentIntentEventType.ROLLOUT_WAVE_STARTED, request.actor(), request.reason(),
                Jsonb.object().put("waveNumber", next.waveNumber()));
        return advanced;
    }

    @Transactional
    public RolloutExecutionResponse pause(UUID projectId, UUID rolloutId, RolloutActionRequest request) {
        RolloutExecutionResponse rollout = get(projectId, rolloutId);
        if (rollout.status() == RolloutStatus.PAUSED) {
            if (Objects.equals(rollout.pausedBy(), request.actor()) && Objects.equals(rollout.pauseReason(), request.reason())) {
                return rollout;
            }
            throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_ALREADY_PAUSED", "Rollout is already paused");
        }
        if (rollout.status() != RolloutStatus.RUNNING && rollout.status() != RolloutStatus.WAITING_FOR_GATES) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_PAUSE_BLOCKED", "Only active rollouts can be paused");
        }
        RolloutExecutionResponse paused = rolloutRepository.pause(rolloutId, request);
        eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                DeploymentIntentEventType.ROLLOUT_PAUSED, request.actor(), request.reason(), Jsonb.object());
        return paused;
    }

    @Transactional
    public RolloutExecutionResponse resume(UUID projectId, UUID rolloutId, RolloutActionRequest request) {
        RolloutExecutionResponse rollout = get(projectId, rolloutId);
        if (rollout.status() != RolloutStatus.PAUSED) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_RESUME_BLOCKED", "Only paused rollouts can resume");
        }
        RolloutExecutionResponse resumed = rolloutRepository.resume(rolloutId, request);
        eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                DeploymentIntentEventType.ROLLOUT_RESUMED, request.actor(), request.reason(), Jsonb.object());
        return resumed;
    }

    @Transactional
    public RolloutExecutionResponse abort(UUID projectId, UUID rolloutId, RolloutActionRequest request) {
        RolloutExecutionResponse rollout = get(projectId, rolloutId);
        if (rollout.status() == RolloutStatus.ABORTED) {
            if (Objects.equals(rollout.abortedBy(), request.actor()) && Objects.equals(rollout.abortReason(), request.reason())) {
                return rollout;
            }
            throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_ALREADY_ABORTED", "Rollout is already aborted");
        }
        if (rollout.status() == RolloutStatus.SUCCEEDED || rollout.status() == RolloutStatus.FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_ABORT_BLOCKED", "Final rollouts cannot be aborted");
        }
        rolloutRepository.waves(rollout.id()).stream()
                .filter(wave -> wave.status() == RolloutWaveStatus.RUNNING || wave.status() == RolloutWaveStatus.PENDING
                        || wave.status() == RolloutWaveStatus.WAITING_FOR_GATES)
                .forEach(wave -> rolloutRepository.markWave(wave.id(), RolloutWaveStatus.ABORTED, request.reason()));
        releaseLock(rollout, request.actor(), "Rollout aborted");
        RolloutExecutionResponse aborted = rolloutRepository.abort(rolloutId, request);
        eventRepository.record(projectId, rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                DeploymentIntentEventType.ROLLOUT_ABORTED, request.actor(), request.reason(), Jsonb.object());
        return aborted;
    }

    public RolloutEvidenceResponse evidence(UUID projectId, UUID rolloutId) {
        RolloutExecutionResponse rollout = get(projectId, rolloutId);
        List<RolloutWaveEvidenceResponse> waves = rolloutRepository.waves(rolloutId).stream()
                .map(wave -> new RolloutWaveEvidenceResponse(wave.waveNumber(), wave.trafficPercentage(), wave.status().name(),
                        gateExecutionService.waveEvidence(projectId, rollout.deploymentPlanId(), wave.id())))
                .toList();
        RollbackRecommendationResponse recommendation = recommendationRepository.find(projectId, rolloutId).orElse(null);
        return new RolloutEvidenceResponse(rollout.id(), rollout.deploymentPlanId(), rollout.strategy().name(),
                rollout.status().name(), rollout.currentWaveNumber(), waves, recommendation, nextAction(rollout, waves, recommendation));
    }

    public RollbackRecommendationResponse recommendation(UUID projectId, UUID rolloutId) {
        get(projectId, rolloutId);
        return recommendationRepository.find(projectId, rolloutId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLLBACK_RECOMMENDATION_NOT_FOUND", "Rollback recommendation not found"));
    }

    public RollbackRecommendationResponse acknowledgeRecommendation(UUID projectId, UUID recommendationId,
            AcknowledgeRollbackRecommendationRequest request) {
        RollbackRecommendationResponse recommendation = recommendationRepository.findById(projectId, recommendationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLLBACK_RECOMMENDATION_NOT_FOUND", "Rollback recommendation not found"));
        if (recommendation.recommendationStatus() == RollbackRecommendationStatus.ACKNOWLEDGED) {
            return recommendation;
        }
        return recommendationRepository.acknowledge(recommendationId, request);
    }

    private DeploymentPlanResponse requireReadyPlan(UUID projectId, UUID planId) {
        DeploymentPlanResponse plan = planService.get(projectId, planId);
        if (plan.status() != DeploymentPlanStatus.READY) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_NOT_READY_FOR_ROLLOUT", "Only READY plans can start rollout");
        }
        return plan;
    }

    private RolloutExecutionResponse requireActive(RolloutExecutionResponse rollout) {
        if (rollout.status() != RolloutStatus.RUNNING && rollout.status() != RolloutStatus.WAITING_FOR_GATES) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLLOUT_NOT_ACTIVE", "Rollout is not active");
        }
        return rollout;
    }

    private RolloutWaveResponse requireWave(RolloutExecutionResponse rollout, int waveNumber) {
        return rolloutRepository.wave(rollout.id(), waveNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLLOUT_WAVE_NOT_FOUND", "Rollout wave not found"));
    }

    private void createRecommendation(RolloutExecutionResponse rollout, String reason) {
        if (recommendationRepository.findOpen(rollout.projectId(), rollout.id()).isPresent()) {
            return;
        }
        UUID currentArtifact = stateRepository.find(rollout.serviceId(), rollout.environmentId())
                .map(state -> state.currentArtifactId()).orElse(null);
        List<RolloutWaveResponse> waves = rolloutRepository.waves(rollout.id());
        int failedWave = waves.stream().filter(wave -> wave.status() == RolloutWaveStatus.FAILED)
                .mapToInt(RolloutWaveResponse::waveNumber).findFirst().orElse(rollout.currentWaveNumber() == null ? 0 : rollout.currentWaveNumber());
        int completed = (int) waves.stream().filter(wave -> wave.status() == RolloutWaveStatus.PASSED).count();
        int maxReached = waves.stream().filter(wave -> wave.status() == RolloutWaveStatus.PASSED)
                .mapToInt(RolloutWaveResponse::trafficPercentage).max().orElse(0);
        int failedTraffic = waves.stream().filter(wave -> wave.waveNumber() == failedWave)
                .mapToInt(RolloutWaveResponse::trafficPercentage).findFirst().orElse(0);
        RollbackRecommendationResponse recommendation = recommendationRepository.create(rollout, currentArtifact, reason,
                Jsonb.object().put("failedWaveNumber", failedWave)
                        .put("failedTrafficPercentage", failedTraffic)
                        .put("completedWaveCount", completed)
                        .put("maxTrafficPercentageReached", maxReached));
        eventRepository.record(rollout.projectId(), rollout.deploymentPlanId(), rollout.serviceId(), rollout.environmentId(), rollout.artifactId(),
                DeploymentIntentEventType.ROLLBACK_RECOMMENDED, "system", reason,
                Jsonb.object().put("rollbackRecommendationId", recommendation.id().toString()));
    }

    private void releaseLock(RolloutExecutionResponse rollout, String actor, String reason) {
        lockRepository.findActiveByPlan(rollout.projectId(), rollout.deploymentPlanId())
                .ifPresent(lock -> lockRepository.mark(lock.id(), DeploymentLockStatus.RELEASED, actor, reason));
    }

    private String nextAction(RolloutExecutionResponse rollout, List<RolloutWaveEvidenceResponse> waves,
            RollbackRecommendationResponse recommendation) {
        if (rollout.status() == RolloutStatus.FAILED && recommendation != null
                && recommendation.recommendationStatus() == RollbackRecommendationStatus.OPEN) {
            return "ACKNOWLEDGE_ROLLBACK_RECOMMENDATION";
        }
        if (rollout.status() == RolloutStatus.PAUSED) {
            return "RESUME";
        }
        if (rollout.status() != RolloutStatus.RUNNING && rollout.status() != RolloutStatus.WAITING_FOR_GATES) {
            return "NONE";
        }
        return waves.stream()
                .filter(wave -> rollout.currentWaveNumber() != null && wave.waveNumber() == rollout.currentWaveNumber())
                .findFirst()
                .map(wave -> wave.gateEvidence().requiredGatesPassed() ? "ADVANCE_WAVE" : "EVALUATE_GATES")
                .orElse("NONE");
    }
}
