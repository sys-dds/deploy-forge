package com.deployforge.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

abstract class CoreReleaseIntentTestSupport extends PostgresIntegrationTestSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    String uniqueSlug(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    String createProject(MockMvc mockMvc, String slug) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Example Platform",
                                  "slug": "%s",
                                  "description": "Example deployment project",
                                  "ownerTeam": "platform",
                                  "lifecycleStatus": "ACTIVE"
                                }
                                """.formatted(slug)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    String createProject(MockMvc mockMvc) throws Exception {
        return createProject(mockMvc, uniqueSlug("project"));
    }

    String createArchivedProject(MockMvc mockMvc) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Archived Platform",
                                  "slug": "%s",
                                  "lifecycleStatus": "ARCHIVED"
                                }
                                """.formatted(uniqueSlug("archived-project"))))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    String createService(MockMvc mockMvc, String projectId, String slug, String tier, String runtime, String lifecycle)
            throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/services", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "API",
                                  "slug": "%s",
                                  "repositoryUrl": "https://example.com/repo.git",
                                  "serviceTier": "%s",
                                  "runtimeKind": "%s",
                                  "lifecycleStatus": "%s"
                                }
                                """.formatted(slug, tier, runtime, lifecycle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    String createService(MockMvc mockMvc, String projectId) throws Exception {
        return createService(mockMvc, projectId, uniqueSlug("service"), "STANDARD", "SERVICE", "ACTIVE");
    }

    String createCriticalService(MockMvc mockMvc, String projectId) throws Exception {
        return createService(mockMvc, projectId, uniqueSlug("critical-service"), "CRITICAL", "SERVICE", "ACTIVE");
    }

    String createEnvironment(MockMvc mockMvc, String projectId, String name, String type, boolean protectedEnvironment,
            boolean requiresApproval, int sortOrder) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/environments", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "environmentType": "%s",
                                  "protectedEnvironment": %s,
                                  "sortOrder": %d,
                                  "externalTargetId": "target-%s",
                                  "requiresApproval": %s,
                                  "lifecycleStatus": "ACTIVE"
                                }
                                """.formatted(name, type, protectedEnvironment, sortOrder, name, requiresApproval)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    String registerArtifact(MockMvc mockMvc, String projectId, String serviceId, String version, String digest)
            throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/artifacts", projectId, serviceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "%s",
                                  "gitSha": "a1b2c3d4",
                                  "imageDigest": "%s",
                                  "buildNumber": "build-42",
                                  "sourceBranch": "main",
                                  "commitMessage": "Add deployment safety",
                                  "createdBy": "developer@example.com",
                                  "metadata": {"pipeline": "deploy-forge-local"}
                                }
                                """.formatted(version, digest)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    String addEvidence(MockMvc mockMvc, String projectId, String serviceId, String artifactId, String type)
            throws Exception {
        return json(mockMvc.perform(post(
                        "/api/v1/projects/{projectId}/services/{serviceId}/artifacts/{artifactId}/evidence",
                        projectId, serviceId, artifactId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evidenceType": "%s",
                                  "evidenceRef": "https://ci.example/%s/%s",
                                  "evidenceSha": "sha256:%s",
                                  "addedBy": "developer@example.com",
                                  "reason": "Evidence supports deployment readiness",
                                  "metadata": {"passed": true}
                                }
                                """.formatted(type, type.toLowerCase(), UUID.randomUUID(), type.toLowerCase())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    JsonNode createPlan(MockMvc mockMvc, String projectId, String serviceId, String artifactId, String environmentId,
            String strategy, String key) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "serviceId": "%s",
                                  "artifactId": "%s",
                                  "targetEnvironmentId": "%s",
                                  "strategy": "%s",
                                  "reason": "Deploy version",
                                  "requestedBy": "developer@example.com"
                                }
                                """.formatted(serviceId, artifactId, environmentId, strategy)))
                .andExpect(status().isCreated())
                .andReturn());
    }
}
