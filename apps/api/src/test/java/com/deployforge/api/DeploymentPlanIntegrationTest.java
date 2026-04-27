package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DeploymentPlanIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createFetchListAndValidatePlans() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "plan-staging", "STAGING", false, false, 3);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "5.0.0", "sha256:plan");

        String planId = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", uniqueSlug("key"))
                .get("id").asText();

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}", projectId, planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.riskLevel").value("MEDIUM"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(planId));

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans", projectId)
                        .header("Idempotency-Key", uniqueSlug("missing"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "artifactId": "%s",
                                  "targetEnvironmentId": "%s",
                                  "strategy": "BAD",
                                  "reason": "Deploy",
                                  "requestedBy": "dev"
                                }
                                """.formatted(serviceId, artifactId, envId)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}", projectId, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
