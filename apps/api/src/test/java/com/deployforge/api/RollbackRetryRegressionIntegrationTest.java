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
class RollbackRetryRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void failedRollbackRetriesSameExecutionOnceAndNonFailedCannotRetry() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();

        mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-executions/{rollbackExecutionId}/retry", ready.projectId(), rollbackId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedBy":"release-manager@example.com","reason":"Too early"}
                                """))
                .andExpect(status().isConflict());

        completeRollbackFailure(mockMvc, ready.projectId(), rollbackId);
        JsonNode retried = retryRollback(mockMvc, ready.projectId(), rollbackId);
        assertThat(retried.get("id").asText()).isEqualTo(rollbackId);
        assertThat(retried.get("retryCount").asInt()).isEqualTo(1);
    }
}
