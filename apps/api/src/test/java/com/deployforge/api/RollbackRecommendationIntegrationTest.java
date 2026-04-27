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
class RollbackRecommendationIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void recommendationCanBeAcknowledgedWithoutExecutingRollback() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves/1/gates/evaluate", ready.projectId(), rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateDefinitionIds":["%s","%s"],"requestedBy":"release-manager@example.com","metrics":{"error_rate":2.5}}
                                """.formatted(ready.syntheticGateId(), ready.metricGateId())))
                .andExpect(status().isOk());
        JsonNode recommendation = json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/rollback-recommendation",
                        ready.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-recommendations/{recommendationId}/acknowledge",
                        ready.projectId(), recommendation.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgedBy\":\"release-manager@example.com\",\"reason\":\"Handled manually\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationStatus").value("ACKNOWLEDGED"));
    }
}
