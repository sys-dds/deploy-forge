package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class RolloutResumeIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void pausedRolloutResumes() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/pause", ready.projectId(), rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"Investigate\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/resume", ready.projectId(), rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"Recovered\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }
}
