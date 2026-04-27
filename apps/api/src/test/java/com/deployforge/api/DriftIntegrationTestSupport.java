package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

abstract class DriftIntegrationTestSupport extends RolloutIntegrationTestSupport {
    @Autowired
    JdbcTemplate jdbcTemplate;

    JsonNode registerTarget(MockMvc mockMvc, String projectId, String serviceId, String environmentId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/runtime-targets",
                        projectId, serviceId, environmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetKey": "%s",
                                  "targetType": "SIMULATED_RUNTIME",
                                  "displayName": "Simulated runtime",
                                  "registeredBy": "operator@example.com",
                                  "metadata": {"region": "local"}
                                }
                                """.formatted(uniqueSlug("target"))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode heartbeat(MockMvc mockMvc, String projectId, String targetId, OffsetDateTime at) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runtime-targets/{targetId}/heartbeat", projectId, targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "HEALTHY",
                                  "reportedBy": "target-agent",
                                  "heartbeatAt": "%s",
                                  "metadata": {"version": "simulated-agent-1"}
                                }
                                """.formatted(at.toInstant())))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode targets(MockMvc mockMvc, String projectId, String serviceId, String environmentId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/runtime-targets",
                        projectId, serviceId, environmentId))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode deploymentReport(MockMvc mockMvc, String projectId, String targetId, String artifactId, String digest,
            String statusValue) throws Exception {
        String artifactPart = artifactId == null ? "" : "\"reportedArtifactId\": \"%s\",".formatted(artifactId);
        String digestPart = digest == null ? "" : "\"reportedImageDigest\": \"%s\",".formatted(digest);
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runtime-targets/{targetId}/deployment-reports", projectId, targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  %s
                                  %s
                                  "reportedVersion": "1.0.0",
                                  "reportStatus": "%s",
                                  "reportedBy": "target-agent",
                                  "observedAt": "%s",
                                  "metadata": {"podCount": 3}
                                }
                                """.formatted(artifactPart, digestPart, statusValue, OffsetDateTime.now(ZoneOffset.UTC).toInstant())))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode configReport(MockMvc mockMvc, String projectId, String targetId, String configVersion, String statusValue) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runtime-targets/{targetId}/config-reports", projectId, targetId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configVersion": "%s",
                                  "configDigest": "sha256:%s",
                                  "reportStatus": "%s",
                                  "reportedBy": "target-agent",
                                  "observedAt": "%s",
                                  "metadata": {"source": "simulated"}
                                }
                                """.formatted(configVersion, configVersion, statusValue, OffsetDateTime.now(ZoneOffset.UTC).toInstant())))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode checkDrift(MockMvc mockMvc, String projectId, String serviceId, String environmentId) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/drift/check",
                        projectId, serviceId, environmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedBy":"operator@example.com","reason":"Manual drift check"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode listDrift(MockMvc mockMvc, String projectId, String statusValue) throws Exception {
        return json(mockMvc.perform(get("/api/v1/projects/{projectId}/drift-findings", projectId)
                        .param("status", statusValue))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode desiredState(MockMvc mockMvc, String projectId, String serviceId, String environmentId) throws Exception {
        return json(mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/desired-state",
                        projectId, serviceId, environmentId))
                .andExpect(status().isOk())
                .andReturn());
    }

    void setDesiredState(String projectId, String serviceId, String environmentId, String artifactId, String digest, String configVersion) {
        jdbcTemplate.update("""
                insert into desired_environment_states (
                    id, project_id, service_id, environment_id, desired_artifact_id, desired_image_digest,
                    desired_config_version, desired_source, desired_reason, recorded_by
                )
                values (gen_random_uuid(), ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?, ?, 'MANUAL_BASELINE', 'Test baseline', 'test')
                on conflict (service_id, environment_id)
                do update set desired_artifact_id = excluded.desired_artifact_id,
                    desired_image_digest = excluded.desired_image_digest,
                    desired_config_version = excluded.desired_config_version,
                    desired_source = excluded.desired_source,
                    desired_reason = excluded.desired_reason,
                    recorded_by = excluded.recorded_by,
                    updated_at = now()
                """, projectId, serviceId, environmentId, artifactId, digest, configVersion);
    }

    JsonNode createBasicDrift(MockMvc mockMvc) throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String prodId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 10);
        String desiredArtifact = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v2"), "sha256:desired");
        String actualArtifact = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v1"), "sha256:actual");
        setDesiredState(projectId, serviceId, prodId, desiredArtifact, "sha256:desired", "config-v2");
        String targetId = registerTarget(mockMvc, projectId, serviceId, prodId).get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, projectId, targetId, actualArtifact, "sha256:actual", "RUNNING");
        return checkDrift(mockMvc, projectId, serviceId, prodId);
    }
}
