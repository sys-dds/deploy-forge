package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ArtifactDeployabilityIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deployabilityReflectsReadinessLifecycleAndEvidence() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String devId = createEnvironment(mockMvc, projectId, "deployability-dev", "DEV", false, false, 1);
        String prodId = createEnvironment(mockMvc, projectId, "deployability-prod", "PROD", true, true, 4);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "4.0.0", "sha256:deployable");

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/deployability",
                        projectId, serviceId, artifactId)
                        .param("environmentId", devId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deployable").value(true));

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/deployability",
                        projectId, serviceId, artifactId)
                        .param("environmentId", prodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deployable").value(false));

        addEvidence(mockMvc, projectId, serviceId, artifactId, "TEST_REPORT");
        addEvidence(mockMvc, projectId, serviceId, artifactId, "IMAGE_SCAN");

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/deployability",
                        projectId, serviceId, artifactId)
                        .param("environmentId", prodId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deployable").value(true));

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts", projectId, serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "4.0.1",
                                  "gitSha": "a1b2c3d4",
                                  "imageDigest": "sha256:blocked",
                                  "createdBy": "developer@example.com",
                                  "readinessStatus": "BLOCKED"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.readinessStatus").value("BLOCKED"));
    }
}
