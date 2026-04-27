package com.deployforge.api;

import static org.hamcrest.Matchers.hasSize;
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
class DomainFoundationIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void projectServiceAndEnvironmentApisWork() throws Exception {
        String projectId = createProject("Example Platform", "example-platform");

        mockMvc.perform(get("/api/v1/projects/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.name").value("Example Platform"))
                .andExpect(jsonPath("$.slug").value("example-platform"))
                .andExpect(jsonPath("$.description").value("Example deployment project"));

        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + projectId + "')]").exists());

        mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API",
                                  "slug": "api",
                                  "repositoryUrl": "https://example.com/repo.git"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.name").value("API"))
                .andExpect(jsonPath("$.slug").value("api"))
                .andExpect(jsonPath("$.repositoryUrl").value("https://example.com/repo.git"));

        mockMvc.perform(get("/api/v1/projects/{projectId}/services", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value("api"));

        createEnvironment(projectId, "development", "DEV", false, 1);
        createEnvironment(projectId, "qa", "QA", false, 2);
        createEnvironment(projectId, "staging", "STAGING", false, 3);
        createEnvironment(projectId, "production", "PROD", true, 4);

        mockMvc.perform(get("/api/v1/projects/{projectId}/environments", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].environmentType").value("DEV"))
                .andExpect(jsonPath("$[3].environmentType").value("PROD"));
    }

    @Test
    void duplicateProjectSlugIsRejected() throws Exception {
        createProject("Duplicate One", "duplicate-project");

        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Duplicate Two",
                                  "slug": "duplicate-project",
                                  "description": "duplicate"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void duplicateServiceSlugPerProjectIsRejected() throws Exception {
        String projectId = createProject("Service Duplicate Project", "service-duplicate-project");
        createService(projectId, "API One", "api");

        mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API Two",
                                  "slug": "api",
                                  "repositoryUrl": "https://example.com/other.git"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidEnvironmentTypeIsRejected() throws Exception {
        String projectId = createProject("Invalid Environment Project", "invalid-environment-project");

        mockMvc.perform(post("/api/v1/projects/{projectId}/environments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "invalid",
                                  "environmentType": "INVALID",
                                  "protectedEnvironment": false,
                                  "sortOrder": 9
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingProjectIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/services", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API",
                                  "slug": "api",
                                  "repositoryUrl": "https://example.com/repo.git"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    private String createProject(String name, String slug) throws Exception {
        return mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "slug": "%s",
                                  "description": "Example deployment project"
                                }
                                """.formatted(name, slug)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    private void createService(String projectId, String name, String slug) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "slug": "%s",
                                  "repositoryUrl": "https://example.com/repo.git"
                                }
                                """.formatted(name, slug)))
                .andExpect(status().isCreated());
    }

    private void createEnvironment(String projectId, String name, String type, boolean protectedEnvironment,
            int sortOrder) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/environments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "environmentType": "%s",
                                  "protectedEnvironment": %s,
                                  "sortOrder": %d
                                }
                                """.formatted(name, type, protectedEnvironment, sortOrder)))
                .andExpect(status().isCreated());
    }
}
