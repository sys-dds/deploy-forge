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
class FoundationMetadataIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void metadataIsSavedReturnedAndValidated() throws Exception {
        String projectId = createProject(mockMvc, uniqueSlug("metadata-project"));
        mockMvc.perform(get("/api/v1/projects/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerTeam").value("platform"))
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));

        String serviceId = createService(mockMvc, projectId, uniqueSlug("metadata-service"), "CRITICAL", "WORKER", "ACTIVE");
        mockMvc.perform(get("/api/v1/projects/{projectId}/services", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serviceTier").value("CRITICAL"))
                .andExpect(jsonPath("$[0].runtimeKind").value("WORKER"));

        String environmentId = createEnvironment(mockMvc, projectId, "metadata-prod", "PROD", true, true, 4);
        mockMvc.perform(get("/api/v1/projects/{projectId}/environments", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].externalTargetId").value("target-metadata-prod"))
                .andExpect(jsonPath("$[0].requiresApproval").value(true));

        mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"API","slug":"%s","serviceTier":"BAD","runtimeKind":"SERVICE"}
                                """.formatted(uniqueSlug("bad-tier"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"API","slug":"%s","serviceTier":"STANDARD","runtimeKind":"BAD"}
                                """.formatted(uniqueSlug("bad-runtime"))))
                .andExpect(status().isBadRequest());

        // Archived records can still be represented by the foundation API.
        createService(mockMvc, projectId, uniqueSlug("archived-service"), "STANDARD", "SERVICE", "ARCHIVED");
        mockMvc.perform(get("/api/v1/projects/{projectId}/services", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.lifecycleStatus == 'ARCHIVED')]").exists());
    }
}
