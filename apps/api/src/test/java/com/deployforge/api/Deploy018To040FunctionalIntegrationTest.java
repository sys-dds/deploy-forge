package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class Deploy018To040FunctionalIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void governanceApprovalPromotionGateLockAndAbortProof() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String devId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 10);
        String stagingId = createEnvironment(mockMvc, projectId, "STAGING-" + uniqueSlug("env"), "STAGING", false, false, 20);
        String prodId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 30);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "18.40.0", "sha256:deploy018040");
        addEvidence(mockMvc, projectId, serviceId, artifactId, "TEST_REPORT");
        addEvidence(mockMvc, projectId, serviceId, artifactId, "IMAGE_SCAN");

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredSourceEnvironmentId").value(stagingId));

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireApproval").value(true));

        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, prodId, "CANARY", uniqueSlug("prod-plan"));
        String planId = plan.get("id").asText();
        assertThat(plan.get("riskLevel").asText()).isEqualTo("CRITICAL");

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness", projectId, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyToStart").value(false))
                .andExpect(jsonPath("$.recommendedAction").value("ADD_PROMOTION_EVIDENCE"));

        String approvalId = json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/approval-requests",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedBy": "developer@example.com",
                                  "reason": "Production deployment requires approval",
                                  "requiredApprovalCount": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();

        mockMvc.perform(post("/api/v1/projects/{projectId}/approval-requests/{approvalRequestId}/decisions", projectId, approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "decidedBy": "approver@example.com",
                                  "reason": "Change reviewed"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/promotion-evidence",
                        projectId, serviceId, artifactId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceEnvironmentId": "%s",
                                  "targetEnvironmentId": "%s",
                                  "evidenceType": "SUCCESSFUL_SOURCE_DEPLOYMENT",
                                  "evidenceRef": "staging-plan-123",
                                  "recordedBy": "release-manager@example.com",
                                  "reason": "Artifact passed staging validation",
                                  "metadata": {"sourcePlanId": "staging-plan-123"}
                                }
                                """.formatted(stagingId, prodId)))
                .andExpect(status().isCreated());

        String syntheticGateId = createGate("""
                {
                  "environmentId": "%s",
                  "name": "prod-deployability-%s",
                  "gateType": "SYNTHETIC_CHECK",
                  "required": true,
                  "enabled": true,
                  "timeoutSeconds": 5,
                  "config": {"checkName": "DEPLOYABILITY_PASSED"},
                  "createdBy": "release-manager@example.com"
                }
                """.formatted(prodId, uniqueSlug("gate")), projectId);
        String metricGateId = createGate("""
                {
                  "environmentId": "%s",
                  "name": "prod-error-rate-%s",
                  "gateType": "METRIC_THRESHOLD",
                  "required": true,
                  "enabled": true,
                  "timeoutSeconds": 5,
                  "config": {"metricName": "error_rate", "operator": "LESS_THAN_OR_EQUAL", "threshold": 1.0},
                  "createdBy": "release-manager@example.com"
                }
                """.formatted(prodId, uniqueSlug("gate")), projectId);

        JsonNode attempts = json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/gates/evaluate",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "gateDefinitionIds": ["%s", "%s"],
                                  "requestedBy": "developer@example.com",
                                  "metrics": {"error_rate": 2.0}
                                }
                                """.formatted(syntheticGateId, metricGateId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn());
        String failedAttemptId = attempts.get(1).get("id").asText();

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness", projectId, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedAction").value("RUN_GATES"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/gate-attempts/{gateAttemptId}/rerun", projectId, failedAttemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedBy": "developer@example.com",
                                  "reason": "Retry after metric recovered",
                                  "metrics": {"error_rate": 0.2}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/gates/evidence", projectId, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredGatesPassed").value(true));

        String lockId = json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/locks/acquire",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lockOwner": "runner-or-operator-1",
                                  "reason": "Preparing deployment",
                                  "ttlSeconds": 300
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness", projectId, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyToStart").value(true))
                .andExpect(jsonPath("$.recommendedAction").value("READY"));

        JsonNode secondPlan = createPlan(mockMvc, projectId, serviceId, artifactId, prodId, "CANARY", uniqueSlug("second-plan"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/locks/acquire", projectId, secondPlan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lockOwner": "runner-or-operator-2",
                                  "reason": "Competing deployment",
                                  "ttlSeconds": 300
                                }
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/abort", projectId, secondPlan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "abortedBy": "developer@example.com",
                                  "reason": "Deployment no longer needed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABORTED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-locks/{lockId}/release", projectId, lockId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "releasedBy": "runner-or-operator-1",
                                  "reason": "Deployment preparation complete"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-intent-events", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItem("PROMOTION_RULE_UPSERTED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("PROTECTED_POLICY_UPSERTED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("APPROVAL_REQUESTED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("APPROVAL_APPROVED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("PROMOTION_EVIDENCE_RECORDED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("GATE_DEFINITION_CREATED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("GATE_EVALUATED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("GATE_RERUN_REQUESTED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("DEPLOYMENT_LOCK_ACQUIRED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("DEPLOYMENT_LOCK_RELEASED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("PLAN_ABORTED")));

        assertTableAbsent("canary_waves");
        assertTableAbsent("rollout_executions");
        assertTableAbsent("rollback_plans");
        assertTableAbsent("runner_leases");
    }

    private String createGate(String body, String projectId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/gate-definitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();
    }

    private void assertTableAbsent(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?
                """, Integer.class, tableName);
        assertThat(count).isZero();
    }
}
