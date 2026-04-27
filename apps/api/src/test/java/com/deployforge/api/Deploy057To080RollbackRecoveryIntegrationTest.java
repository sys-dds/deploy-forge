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
class Deploy057To080RollbackRecoveryIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void failedCanaryRollsBackToPreviousStableArtifactAndVerifiesCleanly() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());

        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        assertThat(recommendation.get("recommendedArtifactId").asText()).isEqualTo(stableArtifactId);
        assertThat(recommendation.get("metadata").get("failedWaveNumber").asInt()).isEqualTo(2);

        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();
        JsonNode runningState = json(mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        ready.projectId(), ready.serviceId(), ready.prodId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(runningState.get("stateStatus").asText()).isEqualTo("ROLLBACK_RUNNING");

        evaluateRollbackGates(mockMvc, ready.projectId(), rollbackId, ready.syntheticGateId(), ready.metricGateId(), 0.1);
        completeRollbackSuccess(mockMvc, ready.projectId(), rollbackId);

        JsonNode finalState = json(mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        ready.projectId(), ready.serviceId(), ready.prodId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(finalState.get("stateStatus").asText()).isEqualTo("ROLLED_BACK");
        assertThat(finalState.get("currentArtifactId").asText()).isEqualTo(stableArtifactId);

        JsonNode evidence = json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollback-executions/{rollbackId}/evidence",
                        ready.projectId(), rollbackId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(evidence.get("status").asText()).isEqualTo("SUCCEEDED");

        JsonNode recovery = json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/recovery",
                        ready.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(recovery.get("recommendedAction").asText()).isEqualTo("NONE");

        JsonNode consistency = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-consistency", ready.projectId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(consistency.get("consistent").asBoolean()).isTrue();

        JsonNode timeline = json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/recovery-timeline",
                        ready.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(timeline.get("events").toString()).contains("ROLLOUT_FAILED", "ROLLBACK_RECOMMENDED", "ROLLBACK_STARTED", "ROLLBACK_SUCCEEDED");
    }
}
