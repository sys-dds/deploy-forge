package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RollbackDriftInteractionIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate localJdbcTemplate;

    @Test
    void rollbackSuccessUpdatesDesiredStateToRollbackTarget() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:stable");
        setCurrentArtifact(localJdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();
        evaluateRollbackGates(mockMvc, ready.projectId(), rollbackId, ready.syntheticGateId(), ready.metricGateId(), 0.1);
        completeRollbackSuccess(mockMvc, ready.projectId(), rollbackId);
        assertThat(desiredState(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId()).get("desiredArtifactId").asText()).isEqualTo(stableArtifactId);
    }
}
