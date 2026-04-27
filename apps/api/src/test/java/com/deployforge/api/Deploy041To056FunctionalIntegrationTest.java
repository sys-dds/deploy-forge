package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class Deploy041To056FunctionalIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void canaryRolloutFailureRecommendationAndSuccessProof() throws Exception {
        ReadyPlan failure = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, failure.projectId(), failure.serviceId(), uniqueSlug("stable"), "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, failure.projectId(), failure.serviceId(), failure.prodId(), stableArtifactId, failure.planId());
        String failedRolloutId = startRollout(mockMvc, failure).get("id").asText();
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves", failure.projectId(), failedRolloutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].trafficPercentage", contains(5, 25, 50, 100)));
        passWave(failure, failedRolloutId, 1);
        advance(failure.projectId(), failedRolloutId);
        passWave(failure, failedRolloutId, 2);
        advance(failure.projectId(), failedRolloutId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves/3/gates/evaluate", failure.projectId(), failedRolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateDefinitionIds":["%s","%s"],"requestedBy":"release-manager@example.com","metrics":{"error_rate":2.0}}
                                """.formatted(failure.syntheticGateId(), failure.metricGateId())))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}", failure.projectId(), failedRolloutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        JsonNode recommendation = json(mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}/rollback-recommendation",
                        failure.projectId(), failedRolloutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedArtifactId").value(stableArtifactId))
                .andReturn());
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollback-recommendations/{recommendationId}/acknowledge",
                        failure.projectId(), recommendation.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgedBy\":\"release-manager@example.com\",\"reason\":\"Acknowledged\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        failure.projectId(), failure.serviceId(), failure.prodId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentArtifactId").value(stableArtifactId));

        ReadyPlan success = readyPlan(mockMvc);
        String successRolloutId = startRollout(mockMvc, success).get("id").asText();
        for (int wave = 1; wave <= 4; wave++) {
            passWave(success, successRolloutId, wave);
            advance(success.projectId(), successRolloutId);
        }
        mockMvc.perform(get("/api/v1/projects/{projectId}/rollouts/{rolloutId}", success.projectId(), successRolloutId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        success.projectId(), success.serviceId(), success.prodId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentArtifactId").value(success.artifactId()));
        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-intent-events", success.projectId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].eventType", hasItem("ROLLOUT_STARTED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("ROLLOUT_WAVE_STARTED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("ROLLOUT_WAVE_COMPLETED")))
                .andExpect(jsonPath("$[*].eventType", hasItem("ROLLOUT_SUCCEEDED")));

        assertTableAbsent("rollback_executions");
        assertTableAbsent("drift_detections");
        assertTableAbsent("runner_leases");
    }

    private void passWave(ReadyPlan ready, String rolloutId, int wave) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/waves/{wave}/gates/evaluate",
                        ready.projectId(), rolloutId, wave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateDefinitionIds":["%s","%s"],"requestedBy":"release-manager@example.com","metrics":{"error_rate":0.2}}
                                """.formatted(ready.syntheticGateId(), ready.metricGateId())))
                .andExpect(status().isOk());
    }

    private void advance(String projectId, String rolloutId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/advance", projectId, rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"Advance\"}"))
                .andExpect(status().isOk());
    }

    private void assertTableAbsent(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?
                """, Integer.class, tableName);
        assertThat(count).isZero();
    }
}
