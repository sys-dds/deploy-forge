package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class RolloutPreflightReadinessIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void failedPreflightCreatesNoRolloutRows() throws Exception {
        Integer before = jdbcTemplate.queryForObject("select count(*) from rollout_executions", Integer.class);
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String prodId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 30);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:" + uniqueSlug("artifact"));
        addEvidence(mockMvc, projectId, serviceId, artifactId, "TEST_REPORT");
        addEvidence(mockMvc, projectId, serviceId, artifactId, "IMAGE_SCAN");
        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, prodId, "CANARY", uniqueSlug("plan"));
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/rollouts/start", projectId, plan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startedBy\":\"release-manager@example.com\",\"reason\":\"Start\"}"))
                .andExpect(status().isConflict());
        Integer after = jdbcTemplate.queryForObject("select count(*) from rollout_executions", Integer.class);
        assertThat(after).isEqualTo(before);
    }
}
