package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ManualInterventionRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void failedRollbackCanBeMarkedManualWithoutChangingCurrentArtifact() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();
        completeRollbackFailure(mockMvc, ready.projectId(), rollbackId);

        JsonNode recovery = json(mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/mark-manual-intervention",
                        ready.projectId(), rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actor":"operator@example.com","reason":"Manual repair required","riskAcknowledgement":"ack"}
                                """))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(recovery.get("recoveryStatus").asText()).isEqualTo("MANUAL_INTERVENTION_REQUIRED");
        String current = jdbcTemplate.queryForObject("""
                select current_artifact_id::text from environment_deployment_states where service_id = ?::uuid and environment_id = ?::uuid
                """, String.class, ready.serviceId(), ready.prodId());
        assertThat(current).isEqualTo(stableArtifactId);
    }
}
