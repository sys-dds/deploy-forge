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
class DeploymentPlanIdempotencyIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void idempotencyKeyIsProjectScopedAndPayloadSensitive() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "idem-dev", "DEV", false, false, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "6.0.0", "sha256:idem");
        String key = uniqueSlug("idem-key");

        JsonNode first = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", key);
        JsonNode second = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", key);
        assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());

        String artifact2 = registerArtifact(mockMvc, projectId, serviceId, "6.0.1", "sha256:idem2");
        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "artifactId": "%s",
                                  "targetEnvironmentId": "%s",
                                  "strategy": "ALL_AT_ONCE",
                                  "reason": "Deploy version",
                                  "requestedBy": "developer@example.com"
                                }
                                """.formatted(serviceId, artifact2, envId)))
                .andExpect(status().isConflict());

        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from deployment_plans where project_id = ? and idempotency_key = ?",
                Integer.class,
                java.util.UUID.fromString(projectId),
                key
        );
        assertThat(count).isEqualTo(1);
    }
}
