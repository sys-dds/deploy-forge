package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class RolloutEvidenceApiIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void evidenceIncludesWavesAndNextAction() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/evidence", ready.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.waves[0].waveNumber").value(1))
                .andExpect(jsonPath("$.recommendedNextAction").value("EVALUATE_GATES"));
    }
}
