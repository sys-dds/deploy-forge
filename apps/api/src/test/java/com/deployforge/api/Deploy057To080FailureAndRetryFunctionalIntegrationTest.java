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
class Deploy057To080FailureAndRetryFunctionalIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void failedRollbackCanEnterManualInterventionThenRetryAndSucceed() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());

        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();

        evaluateRollbackGates(mockMvc, ready.projectId(), rollbackId, ready.syntheticGateId(), ready.metricGateId(), 5.0);
        completeRollbackFailure(mockMvc, ready.projectId(), rollbackId);

        JsonNode manualState = json(mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        ready.projectId(), ready.serviceId(), ready.prodId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(manualState.get("stateStatus").asText()).isEqualTo("MANUAL_INTERVENTION_REQUIRED");

        JsonNode recovery = json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/recovery",
                        ready.projectId(), rolloutId))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(recovery.get("recommendedAction").asText()).isEqualTo("RETRY_ROLLBACK");

        JsonNode retried = retryRollback(mockMvc, ready.projectId(), rollbackId);
        assertThat(retried.get("retryCount").asInt()).isEqualTo(1);
        evaluateRollbackGates(mockMvc, ready.projectId(), rollbackId, ready.syntheticGateId(), ready.metricGateId(), 0.1);
        completeRollbackSuccess(mockMvc, ready.projectId(), rollbackId);

        JsonNode finalState = json(mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        ready.projectId(), ready.serviceId(), ready.prodId()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(finalState.get("stateStatus").asText()).isEqualTo("ROLLED_BACK");
        assertThat(finalState.get("currentArtifactId").asText()).isEqualTo(stableArtifactId);
    }
}
