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
class DeploymentPlanLifecycleIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void readyPlanCanBeCancelledAndEventIsRecorded() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "lifecycle-dev", "DEV", false, false, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "9.0.0", "sha256:lifecycle");
        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", uniqueSlug("lifecycle-key"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/cancel", projectId, plan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"developer@example.com\",\"reason\":\"No longer needed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}", projectId, plan.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-intent-events", projectId)
                        .param("eventType", "PLAN_CANCELLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("PLAN_CANCELLED"));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/cancel", projectId, plan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancelledBy\":\"\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
