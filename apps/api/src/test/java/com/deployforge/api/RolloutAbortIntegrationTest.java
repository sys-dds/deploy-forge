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
class RolloutAbortIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void activeRolloutAbortsAndCannotResume() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/abort", ready.projectId(), rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"Cancel\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABORTED"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/resume", ready.projectId(), rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"Nope\"}"))
                .andExpect(status().isConflict());
    }
}
