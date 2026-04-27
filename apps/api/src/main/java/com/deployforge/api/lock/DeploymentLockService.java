package com.deployforge.api.lock;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.deployforge.api.event.DeploymentIntentEventRepository;
import com.deployforge.api.event.DeploymentIntentEventType;
import com.deployforge.api.plan.DeploymentPlanResponse;
import com.deployforge.api.plan.DeploymentPlanService;
import com.deployforge.api.plan.DeploymentPlanStatus;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DeploymentLockService {
    private final DeploymentPlanService planService;
    private final DeploymentLockRepository lockRepository;
    private final DeploymentIntentEventRepository eventRepository;

    public DeploymentLockService(DeploymentPlanService planService, DeploymentLockRepository lockRepository,
            DeploymentIntentEventRepository eventRepository) {
        this.planService = planService;
        this.lockRepository = lockRepository;
        this.eventRepository = eventRepository;
    }

    public DeploymentLockResponse acquire(UUID projectId, UUID planId, AcquireDeploymentLockRequest request) {
        DeploymentPlanResponse plan = planService.get(projectId, planId);
        if (plan.status() == DeploymentPlanStatus.CANCELLED || plan.status() == DeploymentPlanStatus.ABORTED) {
            throw new ApiException(HttpStatus.CONFLICT, "DEPLOYMENT_LOCK_BLOCKED", "Cancelled or aborted plans cannot acquire locks");
        }
        return lockRepository.findActiveForServiceEnvironment(plan.serviceId(), plan.targetEnvironmentId())
                .map(existing -> {
                    if (existing.deploymentPlanId().equals(planId) && existing.lockOwner().equals(request.lockOwner())) {
                        return existing;
                    }
                    throw new ApiException(HttpStatus.CONFLICT, "DEPLOYMENT_LOCK_ACTIVE", "Another active lock already exists");
                })
                .orElseGet(() -> {
                    DeploymentLockResponse lock = lockRepository.create(projectId, plan.serviceId(), plan.targetEnvironmentId(), planId, request);
                    eventRepository.record(projectId, planId, plan.serviceId(), plan.targetEnvironmentId(), plan.artifactId(),
                            DeploymentIntentEventType.DEPLOYMENT_LOCK_ACQUIRED, request.lockOwner(), request.reason(),
                            Jsonb.object().put("fencingToken", lock.fencingToken()));
                    return lock;
                });
    }

    public List<DeploymentLockResponse> list(UUID projectId) {
        return lockRepository.list(projectId);
    }

    public DeploymentLockResponse release(UUID projectId, UUID lockId, ReleaseDeploymentLockRequest request) {
        DeploymentLockResponse lock = get(projectId, lockId);
        if (lock.status() != DeploymentLockStatus.ACTIVE) {
            return lock;
        }
        DeploymentLockResponse released = lockRepository.mark(lockId, DeploymentLockStatus.RELEASED, request.releasedBy(), request.reason());
        eventRepository.record(projectId, lock.deploymentPlanId(), lock.serviceId(), lock.environmentId(), null,
                DeploymentIntentEventType.DEPLOYMENT_LOCK_RELEASED, request.releasedBy(), request.reason(), Jsonb.object());
        return released;
    }

    public DeploymentLockResponse expire(UUID projectId, UUID lockId, ExpireDeploymentLockRequest request) {
        DeploymentLockResponse lock = get(projectId, lockId);
        if (lock.status() != DeploymentLockStatus.ACTIVE) {
            return lock;
        }
        if (lock.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEPLOYMENT_LOCK_NOT_EXPIRED", "Deployment lock has not expired");
        }
        DeploymentLockResponse expired = lockRepository.mark(lockId, DeploymentLockStatus.EXPIRED, request.actor(), request.reason());
        eventRepository.record(projectId, lock.deploymentPlanId(), lock.serviceId(), lock.environmentId(), null,
                DeploymentIntentEventType.DEPLOYMENT_LOCK_EXPIRED, request.actor(), request.reason(), Jsonb.object());
        return expired;
    }

    public DeploymentLockResponse releaseStale(UUID projectId, UUID lockId, ReleaseDeploymentLockRequest request) {
        DeploymentLockResponse lock = get(projectId, lockId);
        if (lock.status() != DeploymentLockStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "DEPLOYMENT_LOCK_NOT_ACTIVE", "Only active locks can be stale released");
        }
        if (lock.expiresAt().isAfter(OffsetDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DEPLOYMENT_LOCK_NOT_STALE", "Deployment lock is not stale");
        }
        DeploymentLockResponse released = lockRepository.mark(lockId, DeploymentLockStatus.RELEASED, request.releasedBy(), request.reason());
        eventRepository.record(projectId, lock.deploymentPlanId(), lock.serviceId(), lock.environmentId(), null,
                DeploymentIntentEventType.DEPLOYMENT_LOCK_STALE_RELEASED, request.releasedBy(), request.reason(), Jsonb.object());
        return released;
    }

    private DeploymentLockResponse get(UUID projectId, UUID lockId) {
        return lockRepository.find(projectId, lockId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DEPLOYMENT_LOCK_NOT_FOUND", "Deployment lock not found"));
    }
}
