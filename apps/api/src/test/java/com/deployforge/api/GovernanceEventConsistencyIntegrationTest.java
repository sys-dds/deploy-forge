package com.deployforge.api;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class GovernanceEventConsistencyIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void governanceEventsAreRecorded() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-intent-events", ready.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItem("APPROVAL_REQUESTED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("APPROVAL_APPROVED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("PROMOTION_EVIDENCE_RECORDED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("GATE_EVALUATED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("DEPLOYMENT_LOCK_ACQUIRED")));
    }
}
