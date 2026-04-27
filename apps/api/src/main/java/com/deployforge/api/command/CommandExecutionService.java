package com.deployforge.api.command;

import java.util.Map;
import java.util.UUID;

import com.deployforge.api.drift.DriftService;
import com.deployforge.api.rollback.CompleteRollbackRequest;
import com.deployforge.api.rollback.RetryRollbackRequest;
import com.deployforge.api.rollback.RollbackService;
import com.deployforge.api.rollback.StartRollbackRequest;
import com.deployforge.api.reconcile.ReconciliationService;
import com.deployforge.api.rollout.RolloutActionRequest;
import com.deployforge.api.rollout.RolloutService;
import com.deployforge.api.rollout.StartRolloutRequest;
import com.deployforge.api.shared.ApiException;
import com.deployforge.api.shared.Jsonb;
import com.deployforge.api.verify.DeploymentConsistencyVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CommandExecutionService {
    private final RolloutService rolloutService;
    private final RollbackService rollbackService;
    private final DriftService driftService;
    private final DeploymentConsistencyVerifier verifier;
    private final ReconciliationService reconciliationService;

    public CommandExecutionService(RolloutService rolloutService, RollbackService rollbackService, DriftService driftService,
            DeploymentConsistencyVerifier verifier, @Lazy ReconciliationService reconciliationService) {
        this.rolloutService = rolloutService;
        this.rollbackService = rollbackService;
        this.driftService = driftService;
        this.verifier = verifier;
        this.reconciliationService = reconciliationService;
    }

    public JsonNode execute(UUID projectId, Map<String, Object> command) {
        String type = command.get("commandType").toString();
        JsonNode payload = (JsonNode) command.get("payload");
        return switch (type) {
            case "ROLLOUT_START" -> value(rolloutService.start(projectId, uuid(payload, "planId"),
                    new StartRolloutRequest(text(payload, "startedBy"), text(payload, "reason"))));
            case "ROLLOUT_ADVANCE" -> value(rolloutService.advance(projectId, uuid(payload, "rolloutId"),
                    new RolloutActionRequest(text(payload, "advancedBy", "actor"), text(payload, "reason"))));
            case "ROLLOUT_PAUSE" -> value(rolloutService.pause(projectId, uuid(payload, "rolloutId"),
                    new RolloutActionRequest(text(payload, "actor"), text(payload, "reason"))));
            case "ROLLOUT_RESUME" -> value(rolloutService.resume(projectId, uuid(payload, "rolloutId"),
                    new RolloutActionRequest(text(payload, "actor"), text(payload, "reason"))));
            case "ROLLOUT_ABORT" -> value(rolloutService.abort(projectId, uuid(payload, "rolloutId"),
                    new RolloutActionRequest(text(payload, "actor"), text(payload, "reason"))));
            case "ROLLBACK_START" -> value(rollbackService.start(projectId, uuid(payload, "rollbackRecommendationId"),
                    new StartRollbackRequest(text(payload, "startedBy"), text(payload, "reason"))));
            case "ROLLBACK_COMPLETE_SUCCESS" -> value(rollbackService.completeSuccess(projectId, uuid(payload, "rollbackExecutionId"),
                    new CompleteRollbackRequest(text(payload, "actor"), text(payload, "reason"))));
            case "ROLLBACK_COMPLETE_FAILURE" -> value(rollbackService.completeFailure(projectId, uuid(payload, "rollbackExecutionId"),
                    new CompleteRollbackRequest(text(payload, "actor"), text(payload, "reason"))));
            case "ROLLBACK_RETRY" -> value(rollbackService.retry(projectId, uuid(payload, "rollbackExecutionId"),
                    new RetryRollbackRequest(text(payload, "requestedBy"), text(payload, "reason"))));
            case "DRIFT_CHECK" -> value(driftService.check(projectId, uuid(payload, "serviceId"), uuid(payload, "environmentId"),
                    Jsonb.object().put("requestedBy", text(payload, "requestedBy")).put("reason", text(payload, "reason"))));
            case "CREATE_REPAIR_INTENT" -> value(driftService.repairIntent(projectId, uuid(payload, "driftFindingId"),
                    repairIntentPayload(payload)));
            case "VERIFY_CONSISTENCY" -> value(verifier.verify(projectId));
            case "RECONCILE_ENVIRONMENT" -> value(reconciliationService.run(projectId, text(payload, "idempotencyKey"), payload));
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_COMMAND_TYPE", "Unsupported command type: " + type);
        };
    }

    private JsonNode repairIntentPayload(JsonNode payload) {
        ObjectNode request = Jsonb.object();
        request.put("intentType", text(payload, "intentType"));
        request.put("requestedBy", text(payload, "requestedBy"));
        request.put("reason", text(payload, "reason"));
        request.set("metadata", payload.has("metadata") ? payload.get("metadata") : Jsonb.object());
        return request;
    }

    private JsonNode value(Object value) {
        return Jsonb.MAPPER.valueToTree(value);
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(text(node, field));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || node.get(field).asText().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REQUIRED_FIELD_MISSING", field + " is required");
        }
        return node.get(field).asText();
    }

    private static String text(JsonNode node, String first, String fallback) {
        return node != null && node.hasNonNull(first) && !node.get(first).asText().isBlank()
                ? node.get(first).asText() : text(node, fallback);
    }
}
