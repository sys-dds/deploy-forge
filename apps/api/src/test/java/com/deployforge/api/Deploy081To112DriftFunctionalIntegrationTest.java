package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class Deploy081To112DriftFunctionalIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void endToEndDesiredActualDriftLifecycleAndSummary() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String prodId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 1);
        String targetId = registerTarget(mockMvc, projectId, serviceId, prodId).get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        String v1 = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v1"), "sha256:v1");
        String v2 = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v2"), "sha256:v2");
        setDesiredState(projectId, serviceId, prodId, v2, "sha256:v2", "config-v2");

        deploymentReport(mockMvc, projectId, targetId, v1, "sha256:v1", "RUNNING");
        JsonNode drift = checkDrift(mockMvc, projectId, serviceId, prodId);
        String findingId = drift.get("findings").get(0).get("driftFindingId").asText();
        assertThat(drift.get("findings").get(0).get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(json(mockMvc.perform(get("/api/v1/projects/{projectId}/drift-findings/{findingId}/evidence", projectId, findingId))
                .andExpect(status().isOk()).andReturn()).toString()).contains("sha256:v2", "sha256:v1");

        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/acknowledge-manual-change", projectId, findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acknowledgedBy":"operator@example.com","reason":"Emergency change","riskAcknowledgement":"ack"}
                                """))
                .andExpect(status().isOk()).andReturn());
        assertThat(desiredState(mockMvc, projectId, serviceId, prodId).get("desiredImageDigest").asText()).isEqualTo("sha256:v2");

        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/accept-actual-as-desired", projectId, findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acceptedBy":"release-manager@example.com","reason":"Accept actual","riskAcknowledgement":"ack"}
                                """))
                .andExpect(status().isOk()).andReturn());
        assertThat(desiredState(mockMvc, projectId, serviceId, prodId).get("desiredImageDigest").asText()).isEqualTo("sha256:v1");

        configReport(mockMvc, projectId, targetId, "config-v1", "PRESENT");
        JsonNode configDrift = checkDrift(mockMvc, projectId, serviceId, prodId);
        assertThat(configDrift.toString()).contains("CONFIG_DRIFT");
        assertThat(json(mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/actual-state",
                        projectId, serviceId, prodId))
                .andExpect(status().isOk()).andReturn()).toString()).contains("DRIFTED");
    }
}
