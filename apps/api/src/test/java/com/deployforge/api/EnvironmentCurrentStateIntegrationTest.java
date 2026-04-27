package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class EnvironmentCurrentStateIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void stateMovesFromNeverDeployedToPlannedOnPlanCreation() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "state-dev", "DEV", false, false, 1);

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        projectId, serviceId, envId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateStatus").value("NEVER_DEPLOYED"));

        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "10.0.0", "sha256:state");
        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", uniqueSlug("state-key"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/state",
                        projectId, serviceId, envId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateStatus").value("PLANNED"))
                .andExpect(jsonPath("$.lastDeploymentPlanId").value(plan.get("id").asText()));
    }
}
