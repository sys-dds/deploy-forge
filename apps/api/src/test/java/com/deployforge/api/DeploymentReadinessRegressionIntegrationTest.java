package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DeploymentReadinessRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void readyPlanReportsReadyAfterGovernanceAndLock() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness", ready.projectId(), ready.planId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyToStart").value(true))
                .andExpect(jsonPath("$.recommendedAction").value("READY"));
    }
}
