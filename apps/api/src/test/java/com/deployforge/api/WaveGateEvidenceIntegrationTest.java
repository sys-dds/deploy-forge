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
class WaveGateEvidenceIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void waveGateEvidenceTracksAttempts() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        JsonNode rollout = startRollout(mockMvc, ready);
        String rolloutId = rollout.get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves/1/gates/evaluate", ready.projectId(), rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateDefinitionIds":["%s","%s"],"requestedBy":"release-manager@example.com","metrics":{"error_rate":0.2}}
                                """.formatted(ready.syntheticGateId(), ready.metricGateId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves/1/gates/evidence", ready.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredGatesPassed").value(true));
    }
}
