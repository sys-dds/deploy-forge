package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ReleaseArtifactImmutabilityIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void duplicateRegistrationIsIdempotentUnlessImmutableFieldsChange() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "2.0.0", "sha256:same");

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts", projectId, serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "2.0.0",
                                  "gitSha": "a1b2c3d4",
                                  "imageDigest": "sha256:same",
                                  "buildNumber": "build-42",
                                  "sourceBranch": "main",
                                  "commitMessage": "Add deployment safety",
                                  "createdBy": "developer@example.com",
                                  "metadata": {"pipeline": "deploy-forge-local"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(artifactId));

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts", projectId, serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"2.0.0","gitSha":"different","imageDigest":"sha256:same","createdBy":"developer@example.com"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts", projectId, serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"2.0.0","gitSha":"a1b2c3d4","imageDigest":"sha256:different","createdBy":"developer@example.com"}
                                """))
                .andExpect(status().isConflict());
    }
}
