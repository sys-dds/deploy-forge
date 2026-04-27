package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

abstract class RolloutIntegrationTestSupport extends CoreReleaseIntentTestSupport {

    record ReadyPlan(String projectId, String serviceId, String stagingId, String prodId, String artifactId,
            String planId, String syntheticGateId, String metricGateId, String lockId) {
    }

    ReadyPlan readyPlan(MockMvc mockMvc) throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String stagingId = createEnvironment(mockMvc, projectId, "STAGING-" + uniqueSlug("env"), "STAGING", false, false, 20);
        String prodId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 30);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:" + uniqueSlug("artifact"));
        addEvidence(mockMvc, projectId, serviceId, artifactId, "TEST_REPORT");
        addEvidence(mockMvc, projectId, serviceId, artifactId, "IMAGE_SCAN");
        protectAndPromote(mockMvc, projectId, prodId, stagingId);
        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, prodId, "CANARY", uniqueSlug("plan"));
        String planId = plan.get("id").asText();
        approveAndPromote(mockMvc, projectId, serviceId, artifactId, stagingId, prodId, planId);
        String syntheticGateId = createSyntheticGate(mockMvc, projectId, prodId, true);
        String metricGateId = createMetricGate(mockMvc, projectId, prodId, true);
        evaluatePlanGates(mockMvc, projectId, planId, syntheticGateId, metricGateId, 0.2);
        String lockId = json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/locks/acquire", projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lockOwner":"runner","reason":"Ready for rollout","ttlSeconds":300}
                                """))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();
        return new ReadyPlan(projectId, serviceId, stagingId, prodId, artifactId, planId, syntheticGateId, metricGateId, lockId);
    }

    void protectAndPromote(MockMvc mockMvc, String projectId, String prodId, String stagingId) throws Exception {
        mockMvc.perform(put("/api/v1/projects/{projectId}/environments/{environmentId}/promotion-rule", projectId, prodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requiredSourceEnvironmentId": "%s",
                                  "requiresSuccessfulSourceDeployment": true,
                                  "requiresApproval": true,
                                  "allowOverride": true,
                                  "createdBy": "release-manager@example.com"
                                }
                                """.formatted(stagingId)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/projects/{projectId}/environments/{environmentId}/protection-policy", projectId, prodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requireApproval": true,
                                  "requiredApprovalCount": 1,
                                  "requirePromotionEvidence": true,
                                  "allowEmergencyOverride": true,
                                  "maxRiskWithoutOverride": "MEDIUM",
                                  "createdBy": "release-manager@example.com"
                                }
                                """))
                .andExpect(status().isOk());
    }

    void approveAndPromote(MockMvc mockMvc, String projectId, String serviceId, String artifactId, String stagingId,
            String prodId, String planId) throws Exception {
        String approvalId = json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/approval-requests", projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedBy":"developer@example.com","reason":"Needs approval","requiredApprovalCount":1}
                                """))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/approval-requests/{approvalRequestId}/decisions", projectId, approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","decidedBy":"approver@example.com","reason":"Reviewed"}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/promotion-evidence", projectId, serviceId, artifactId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceEnvironmentId": "%s",
                                  "targetEnvironmentId": "%s",
                                  "evidenceType": "SUCCESSFUL_SOURCE_DEPLOYMENT",
                                  "evidenceRef": "%s",
                                  "recordedBy": "release-manager@example.com",
                                  "reason": "Passed staging",
                                  "metadata": {}
                                }
                                """.formatted(stagingId, prodId, UUID.randomUUID())))
                .andExpect(status().isCreated());
    }

    String createSyntheticGate(MockMvc mockMvc, String projectId, String environmentId, boolean required) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/gate-definitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "environmentId": "%s",
                                  "name": "%s",
                                  "gateType": "SYNTHETIC_CHECK",
                                  "required": %s,
                                  "enabled": true,
                                  "timeoutSeconds": 5,
                                  "config": {"checkName": "DEPLOYABILITY_PASSED"},
                                  "createdBy": "release-manager@example.com"
                                }
                                """.formatted(environmentId, uniqueSlug("synthetic-gate"), required)))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();
    }

    String createMetricGate(MockMvc mockMvc, String projectId, String environmentId, boolean required) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/gate-definitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "environmentId": "%s",
                                  "name": "%s",
                                  "gateType": "METRIC_THRESHOLD",
                                  "required": %s,
                                  "enabled": true,
                                  "timeoutSeconds": 5,
                                  "config": {"metricName": "error_rate", "operator": "LESS_THAN_OR_EQUAL", "threshold": 1.0},
                                  "createdBy": "release-manager@example.com"
                                }
                                """.formatted(environmentId, uniqueSlug("metric-gate"), required)))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();
    }

    JsonNode evaluatePlanGates(MockMvc mockMvc, String projectId, String planId, String syntheticGateId, String metricGateId,
            double errorRate) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/gates/evaluate", projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gateDefinitionIds": ["%s", "%s"],
                                  "requestedBy": "developer@example.com",
                                  "metrics": {"error_rate": %s}
                                }
                                """.formatted(syntheticGateId, metricGateId, errorRate)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode startRollout(MockMvc mockMvc, ReadyPlan readyPlan) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/rollouts/start",
                        readyPlan.projectId(), readyPlan.planId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedBy":"release-manager@example.com","reason":"Start rollout"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode evaluateWaveGates(MockMvc mockMvc, String projectId, String rolloutId, int waveNumber,
            String syntheticGateId, String metricGateId, double errorRate) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves/{waveNumber}/gates/evaluate",
                        projectId, rolloutId, waveNumber)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gateDefinitionIds": ["%s", "%s"],
                                  "requestedBy": "release-manager@example.com",
                                  "metrics": {"error_rate": %s}
                                }
                                """.formatted(syntheticGateId, metricGateId, errorRate)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode advanceRollout(MockMvc mockMvc, String projectId, String rolloutId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/advance", projectId, rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"release-manager@example.com","reason":"Advance after gates"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode failSecondWave(MockMvc mockMvc, ReadyPlan readyPlan, String rolloutId) throws Exception {
        evaluateWaveGates(mockMvc, readyPlan.projectId(), rolloutId, 1, readyPlan.syntheticGateId(), readyPlan.metricGateId(), 0.2);
        advanceRollout(mockMvc, readyPlan.projectId(), rolloutId);
        evaluateWaveGates(mockMvc, readyPlan.projectId(), rolloutId, 2, readyPlan.syntheticGateId(), readyPlan.metricGateId(), 5.0);
        return json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/rollback-recommendation",
                        readyPlan.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode startRollback(MockMvc mockMvc, String projectId, String recommendationId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-recommendations/{recommendationId}/rollback-executions/start",
                        projectId, recommendationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedBy":"release-manager@example.com","reason":"Rollback failed canary"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode evaluateRollbackGates(MockMvc mockMvc, String projectId, String rollbackId, String syntheticGateId,
            String metricGateId, double errorRate) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-executions/{rollbackExecutionId}/gates/evaluate",
                        projectId, rollbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gateDefinitionIds": ["%s", "%s"],
                                  "requestedBy": "release-manager@example.com",
                                  "metrics": {"error_rate": %s}
                                }
                                """.formatted(syntheticGateId, metricGateId, errorRate)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode completeRollbackSuccess(MockMvc mockMvc, String projectId, String rollbackId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-executions/{rollbackExecutionId}/complete-success",
                        projectId, rollbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"release-manager@example.com","reason":"Rollback gates passed"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode completeRollbackFailure(MockMvc mockMvc, String projectId, String rollbackId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-executions/{rollbackExecutionId}/complete-failure",
                        projectId, rollbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"release-manager@example.com","reason":"Rollback verification failed"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode retryRollback(MockMvc mockMvc, String projectId, String rollbackId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-executions/{rollbackExecutionId}/retry",
                        projectId, rollbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedBy":"release-manager@example.com","reason":"Retry after operator fix"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    void setCurrentArtifact(JdbcTemplate jdbcTemplate, String projectId, String serviceId, String environmentId,
            String artifactId, String planId) {
        jdbcTemplate.update("""
                insert into environment_deployment_states (
                    id, project_id, service_id, environment_id, current_artifact_id, last_deployment_plan_id, state_status
                )
                values (?, ?, ?, ?, ?, ?, 'DEPLOYED')
                on conflict (service_id, environment_id)
                do update set current_artifact_id = excluded.current_artifact_id, state_status = 'DEPLOYED'
                """, UUID.randomUUID(), UUID.fromString(projectId), UUID.fromString(serviceId), UUID.fromString(environmentId),
                UUID.fromString(artifactId), UUID.fromString(planId));
    }
}
