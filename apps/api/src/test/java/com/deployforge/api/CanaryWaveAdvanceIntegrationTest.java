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
class CanaryWaveAdvanceIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void wavesAdvanceInOrderAndFinalSuccessUpdatesState() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        for (int wave = 1; wave <= 4; wave++) {
            passWave(ready, rolloutId, wave);
            mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/advance", ready.projectId(), rolloutId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"Advance\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}", ready.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        ready.projectId(), ready.serviceId(), ready.prodId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentArtifactId").value(ready.artifactId()));
    }

    private void passWave(ReadyPlan ready, String rolloutId, int wave) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves/{wave}/gates/evaluate", ready.projectId(), rolloutId, wave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateDefinitionIds":["%s","%s"],"requestedBy":"release-manager@example.com","metrics":{"error_rate":0.2}}
                                """.formatted(ready.syntheticGateId(), ready.metricGateId())))
                .andExpect(status().isOk());
    }
}
