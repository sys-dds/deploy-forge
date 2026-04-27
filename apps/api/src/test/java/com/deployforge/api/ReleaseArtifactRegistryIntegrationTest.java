package com.deployforge.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
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
class ReleaseArtifactRegistryIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerFetchListAndValidateArtifacts() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "1.2.3", "sha256:abc123");

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}",
                        projectId, serviceId, artifactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(artifactId))
                .andExpect(jsonPath("$.readinessStatus").value("READY"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/artifacts", projectId, serviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts", projectId, serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"bad","gitSha":"sha","imageDigest":"tag:latest","createdBy":"dev"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}",
                        projectId, UUID.randomUUID(), artifactId))
                .andExpect(status().isNotFound());

        mockMvc.perform(options("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}",
                        projectId, serviceId, artifactId))
                .andExpect(status().isOk());
    }
}
