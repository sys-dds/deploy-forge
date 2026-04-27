package com.deployforge.api;

import static org.hamcrest.Matchers.hasSize;
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
class ReleaseArtifactEvidenceIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void evidenceIsAppendOnlyAndIdempotent() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "3.0.0", "sha256:evidence");

        String body = """
                {
                  "evidenceType": "TEST_REPORT",
                  "evidenceRef": "https://ci.example/build/42/tests",
                  "evidenceSha": "sha256:evidence123",
                  "metadata": {"tests": 184}
                }
                """;
        String evidenceId = json(mockMvc.perform(post(
                        "/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/evidence",
                        projectId, serviceId, artifactId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/evidence",
                        projectId, serviceId, artifactId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(evidenceId));

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/evidence",
                        projectId, serviceId, artifactId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("sha256:evidence123", "sha256:changed")))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/evidence",
                        projectId, serviceId, artifactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/evidence",
                        projectId, serviceId, artifactId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"evidenceType\":\"BAD\",\"evidenceRef\":\"ref\"}"))
                .andExpect(status().isBadRequest());
    }
}
