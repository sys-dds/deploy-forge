package com.deployforge.api;

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
class DeploymentLockRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void activeLockBlocksOtherPlanAndReleases() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:" + uniqueSlug("artifact"));
        JsonNode plan1 = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "CANARY", uniqueSlug("plan"));
        JsonNode plan2 = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "CANARY", uniqueSlug("plan"));
        String lockId = json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/locks/acquire", projectId, plan1.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockOwner\":\"runner\",\"reason\":\"Lock\",\"ttlSeconds\":300}"))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/locks/acquire", projectId, plan2.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockOwner\":\"other\",\"reason\":\"Lock\",\"ttlSeconds\":300}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-locks/{lockId}/release", projectId, lockId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"releasedBy\":\"runner\",\"reason\":\"Done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }
}
