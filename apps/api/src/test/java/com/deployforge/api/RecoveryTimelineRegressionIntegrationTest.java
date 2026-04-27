package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RecoveryTimelineRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void timelineContainsRollbackLifecycleEvents() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();
        evaluateRollbackGates(mockMvc, ready.projectId(), rollbackId, ready.syntheticGateId(), ready.metricGateId(), 0.1);
        completeRollbackSuccess(mockMvc, ready.projectId(), rollbackId);

        JsonNode timeline = json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/recovery-timeline",
                        ready.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(timeline.get("events").toString())
                .contains("ROLLOUT_FAILED", "ROLLBACK_RECOMMENDED", "ROLLBACK_STARTED", "ROLLBACK_GATE_EVALUATED", "ROLLBACK_SUCCEEDED");
    }
}
