package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DeploymentIntentEventLogIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void keyDomainActionsAppendEventsScopedToProject() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "events-dev", "DEV", false, false, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "11.0.0", "sha256:events");
        addEvidence(mockMvc, projectId, serviceId, artifactId, "TEST_REPORT");
        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", uniqueSlug("events-key"));
        createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", plan.get("idempotencyKey").asText());

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/cancel", projectId, plan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"developer@example.com\",\"reason\":\"No longer needed\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-intent-events", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventType == 'ARTIFACT_REGISTERED')]").exists())
                .andExpect(jsonPath("$[?(@.eventType == 'ARTIFACT_EVIDENCE_ADDED')]").exists())
                .andExpect(jsonPath("$[?(@.eventType == 'PLAN_CREATED')]").exists())
                .andExpect(jsonPath("$[?(@.eventType == 'PLAN_IDEMPOTENT_REPLAYED')]").exists())
                .andExpect(jsonPath("$[?(@.eventType == 'PLAN_CANCELLED')]").exists());
    }
}
