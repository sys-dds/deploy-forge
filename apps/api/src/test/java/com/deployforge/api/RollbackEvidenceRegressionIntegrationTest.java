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
class RollbackEvidenceRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void rollbackEvidenceIncludesTargetGatesStateAndFailureReason() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();
        completeRollbackFailure(mockMvc, ready.projectId(), rollbackId);

        JsonNode evidence = json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollback-executions/{rollbackId}/evidence",
                        ready.projectId(), rollbackId))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(evidence.get("targetArtifactId").asText()).isEqualTo(stableArtifactId);
        assertThat(evidence.get("status").asText()).isEqualTo("FAILED");
        assertThat(evidence.get("manualInterventionReason").isNull()).isTrue();
        assertThat(evidence.get("environmentState").get("stateStatus").asText()).isEqualTo("MANUAL_INTERVENTION_REQUIRED");
    }
}
