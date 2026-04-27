package com.deployforge.api;

import static org.hamcrest.Matchers.hasSize;
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
class RolloutStartIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void startRequiresReadinessAndIsIdempotent() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        JsonNode rollout = startRollout(mockMvc, ready);
        startRollout(mockMvc, ready);
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves", ready.projectId(), rollout.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    void startWithoutLockIsBlocked() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:" + uniqueSlug("artifact"));
        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "CANARY", uniqueSlug("plan"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/rollouts/start", projectId, plan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedBy\":\"release-manager@example.com\",\"reason\":\"Start\"}"))
                .andExpect(status().isConflict());
    }
}
