package com.deployforge.api;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class RolloutWaveModelIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void canaryCreatesDefaultWaves() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        JsonNode rollout = startRollout(mockMvc, ready);
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves", ready.projectId(), rollout.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].trafficPercentage", contains(5, 25, 50, 100)))
                .andExpect(jsonPath("$[0].status").value("RUNNING"));
    }

    @Test
    void allAtOnceCreatesSingleWave() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:" + uniqueSlug("artifact"));
        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", uniqueSlug("plan"));
        String planId = plan.get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/locks/acquire", projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockOwner\":\"runner\",\"reason\":\"Lock\",\"ttlSeconds\":300}"))
                .andExpect(status().isOk());
        String rolloutId = json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/rollouts/start", projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedBy\":\"release-manager@example.com\",\"reason\":\"Start\"}"))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves", projectId, rolloutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].trafficPercentage", contains(100)));
    }
}
