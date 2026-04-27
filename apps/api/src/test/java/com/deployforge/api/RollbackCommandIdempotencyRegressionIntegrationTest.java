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
class RollbackCommandIdempotencyRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void duplicateRollbackStartSameCommandReturnsExistingDifferentCommandConflicts() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);

        JsonNode first = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText());
        JsonNode duplicate = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText());

        assertThat(duplicate.get("id").asText()).isEqualTo(first.get("id").asText());
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-recommendations/{recommendationId}/rollback-executions/start",
                        ready.projectId(), recommendation.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startedBy":"another@example.com","reason":"Different rollback"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateRollbackTerminalCommandsDoNotDuplicateStateOrEvents() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();
        evaluateRollbackGates(mockMvc, ready.projectId(), rollbackId, ready.syntheticGateId(), ready.metricGateId(), 0.1);

        completeRollbackSuccess(mockMvc, ready.projectId(), rollbackId);
        completeRollbackSuccess(mockMvc, ready.projectId(), rollbackId);

        Integer succeededEvents = jdbcTemplate.queryForObject("""
                select count(*) from deployment_intent_events where project_id = ?::uuid and event_type = 'ROLLBACK_SUCCEEDED'
                """, Integer.class, ready.projectId());
        assertThat(succeededEvents).isEqualTo(1);
    }
}
