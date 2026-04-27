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
class RolloutIdempotencyIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void duplicateStartAndPauseAreSafe() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        JsonNode rollout = startRollout(mockMvc, ready);
        startRollout(mockMvc, ready);
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves", ready.projectId(), rollout.get("id").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/pause", ready.projectId(), rollout.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"Investigate\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/pause", ready.projectId(), rollout.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"Investigate\"}"))
                .andExpect(status().isOk());
    }
}
