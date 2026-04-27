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
class FoundationValidationIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validationAndConflictErrorsAreConsistent() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad\",\"slug\":\"Bad_Slug\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"slug\":\"blank-project\"}"))
                .andExpect(status().isBadRequest());

        String projectId = createProject(mockMvc, uniqueSlug("validation-project"));
        String serviceSlug = uniqueSlug("validation-service");
        createService(mockMvc, projectId, serviceSlug, "STANDARD", "SERVICE", "ACTIVE");

        mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"API\",\"slug\":\"Bad_Service\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"slug\":\"blank-service\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"API","slug":"%s"}
                                """.formatted(serviceSlug)))
                .andExpect(status().isConflict());

        createEnvironment(mockMvc, projectId, "validation-prod", "PROD", true, true, 4);

        mockMvc.perform(post("/api/v1/projects/{projectId}/environments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"environmentType\":\"DEV\",\"sortOrder\":1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects/{projectId}/environments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"bad-order\",\"environmentType\":\"DEV\",\"sortOrder\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/projects/{projectId}/environments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"validation-prod\",\"environmentType\":\"PROD\",\"sortOrder\":5}"))
                .andExpect(status().isConflict());
    }
}
