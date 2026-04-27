package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class Deploy002To017FunctionalIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void endToEndCoreReleaseIntentIsModeledSafely() throws Exception {
        String projectId = createProject(mockMvc, uniqueSlug("functional-project"));
        String serviceId = createCriticalService(mockMvc, projectId);
        String dev = createEnvironment(mockMvc, projectId, "functional-dev", "DEV", false, false, 1);
        createEnvironment(mockMvc, projectId, "functional-qa", "QA", false, false, 2);
        String staging = createEnvironment(mockMvc, projectId, "functional-staging", "STAGING", false, false, 3);
        String prod = createEnvironment(mockMvc, projectId, "functional-prod", "PROD", true, true, 4);

        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "12.0.0", "sha256:functional");
        addEvidence(mockMvc, projectId, serviceId, artifactId, "TEST_REPORT");

        JsonNode devDeployability = json(mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/deployability",
                        projectId, serviceId, artifactId).param("environmentId", dev))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(devDeployability.get("deployable").asBoolean()).isTrue();

        JsonNode prodBlocked = json(mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/deployability",
                        projectId, serviceId, artifactId).param("environmentId", prod))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(prodBlocked.get("deployable").asBoolean()).isFalse();

        addEvidence(mockMvc, projectId, serviceId, artifactId, "IMAGE_SCAN");
        JsonNode prodReady = json(mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/deployability",
                        projectId, serviceId, artifactId).param("environmentId", prod))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(prodReady.get("deployable").asBoolean()).isTrue();

        String repeatedArtifactId = registerArtifact(mockMvc, projectId, serviceId, "12.0.0", "sha256:functional");
        assertThat(repeatedArtifactId).isEqualTo(artifactId);

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts", projectId, serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"12.0.0","gitSha":"a1b2c3d4","imageDigest":"sha256:changed","createdBy":"developer@example.com"}
                                """))
                .andExpect(status().isConflict());

        JsonNode stagingPlan = createPlan(mockMvc, projectId, serviceId, artifactId, staging,
                "ALL_AT_ONCE", uniqueSlug("functional-staging"));
        assertThat(stagingPlan.get("status").asText()).isEqualTo("READY");
        assertThat(stagingPlan.get("riskLevel").asText()).isEqualTo("MEDIUM");
        assertThat(stagingPlan.get("evidenceSnapshot").has("artifact")).isTrue();

        JsonNode replay = createPlan(mockMvc, projectId, serviceId, artifactId, staging,
                "ALL_AT_ONCE", stagingPlan.get("idempotencyKey").asText());
        assertThat(replay.get("id").asText()).isEqualTo(stagingPlan.get("id").asText());
        assertThat(replay.get("evidenceSnapshot")).isEqualTo(stagingPlan.get("evidenceSnapshot"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans", projectId)
                        .header("Idempotency-Key", stagingPlan.get("idempotencyKey").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "artifactId": "%s",
                                  "targetEnvironmentId": "%s",
                                  "strategy": "CANARY",
                                  "reason": "Deploy version",
                                  "requestedBy": "developer@example.com"
                                }
                                """.formatted(serviceId, artifactId, prod)))
                .andExpect(status().isConflict());

        JsonNode prodPlan = createPlan(mockMvc, projectId, serviceId, artifactId, prod,
                "CANARY", uniqueSlug("functional-prod"));
        assertThat(prodPlan.get("riskLevel").asText()).isEqualTo("CRITICAL");

        JsonNode state = json(mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        projectId, serviceId, prod))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(state.get("stateStatus").asText()).isEqualTo("PLANNED");

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/cancel",
                        projectId, stagingPlan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"developer@example.com\",\"reason\":\"No longer needed\"}"))
                .andExpect(status().isOk());

        JsonNode events = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-intent-events", projectId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(events.toString()).contains("ARTIFACT_REGISTERED", "ARTIFACT_EVIDENCE_ADDED",
                "PLAN_CREATED", "PLAN_IDEMPOTENT_REPLAYED", "PLAN_IDEMPOTENCY_CONFLICT", "PLAN_CANCELLED");
    }
}
