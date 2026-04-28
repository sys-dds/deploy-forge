package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

abstract class AsyncCommandIntegrationTestSupport extends DriftIntegrationTestSupport {
    JsonNode command(MockMvc mockMvc, String projectId, String key, String type, String payload) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/commands", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandType": "%s",
                                  "payload": %s,
                                  "priority": 100,
                                  "maxAttempts": 2,
                                  "createdBy": "operator@example.com",
                                  "reason": "test command"
                                }
                                """.formatted(type, payload)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode registerRunner(MockMvc mockMvc, String projectId, String nodeId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/register", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nodeId":"%s","metadata":{"mode":"test"}}
                                """.formatted(nodeId)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode claim(MockMvc mockMvc, String projectId, String nodeId, int leaseSeconds, String type) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/claim", projectId, nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaseSeconds":%d,"supportedCommandTypes":["%s"]}
                                """.formatted(leaseSeconds, type)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode tick(MockMvc mockMvc, String projectId, String nodeId, String type) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/tick", projectId, nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaseSeconds":60,"supportedCommandTypes":["%s"]}
                                """.formatted(type)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode fail(MockMvc mockMvc, String projectId, String nodeId, String commandId, long token) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/{commandId}/complete-failure",
                        projectId, nodeId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fencingToken":%d,"errorMessage":"transient failure"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode succeed(MockMvc mockMvc, String projectId, String nodeId, String commandId, long token) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/{commandId}/complete-success",
                        projectId, nodeId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fencingToken":%d,"result":{"ok":true}}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode driftPayload(String serviceId, String environmentId) {
        return com.deployforge.api.shared.Jsonb.object()
                .put("serviceId", serviceId)
                .put("environmentId", environmentId)
                .put("requestedBy", "operator@example.com")
                .put("reason", "async drift check");
    }

    JsonNode createDriftFixture(MockMvc mockMvc) throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 1);
        String desired = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v2"), "sha256:desired-" + uniqueSlug("d"));
        String actual = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v1"), "sha256:actual-" + uniqueSlug("a"));
        setDesiredState(projectId, serviceId, envId, desired, "sha256:desired", "config-v2");
        String targetId = registerTarget(mockMvc, projectId, serviceId, envId).get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, projectId, targetId, actual, "sha256:actual", "RUNNING");
        return com.deployforge.api.shared.Jsonb.object()
                .put("projectId", projectId)
                .put("serviceId", serviceId)
                .put("environmentId", envId)
                .put("targetId", targetId);
    }

    JsonNode reconcile(MockMvc mockMvc, String projectId, String key, String serviceId, String environmentId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/reconciliation-runs", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":"%s","environmentId":"%s","requestedBy":"operator@example.com","reason":"plan repair"}
                                """.formatted(serviceId, environmentId)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode approvePlan(MockMvc mockMvc, String projectId, String planId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/repair-plans/{repairPlanId}/approve", projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approvedBy":"release-manager@example.com","reason":"approved"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode recoveryRequest() {
        return com.deployforge.api.shared.Jsonb.object()
                .put("actor", "operator@example.com")
                .put("reason", "operator recovery")
                .put("riskAcknowledgement", "I understand this changes recovery state");
    }

    JsonNode putPolicy(MockMvc mockMvc, String projectId) throws Exception {
        return json(mockMvc.perform(put("/api/v1/projects/{projectId}/reconciliation-policies", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"requireApprovalForRepair":true,"createdBy":"operator@example.com","reason":"default policy"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode getJson(MockMvc mockMvc, String path, Object... args) throws Exception {
        return json(mockMvc.perform(get(path, args)).andExpect(status().isOk()).andReturn());
    }
}
