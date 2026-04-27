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
class GateExecutionRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void metricFailureRerunAndEvidenceLatestAttempt() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        JsonNode attempts = evaluatePlanGates(mockMvc, ready.projectId(), ready.planId(), ready.syntheticGateId(), ready.metricGateId(), 3.0);
        String failedAttemptId = attempts.get(1).get("id").asText();
        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/gates/evidence", ready.projectId(), ready.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredGatesPassed").value(false));
        mockMvc.perform(post("/api/v1/projects/{projectId}/gate-attempts/{gateAttemptId}/rerun", ready.projectId(), failedAttemptId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedBy\":\"dev@example.com\",\"reason\":\"Retry\",\"metrics\":{\"error_rate\":0.1}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/gates/evidence", ready.projectId(), ready.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredGatesPassed").value(true));
    }
}
