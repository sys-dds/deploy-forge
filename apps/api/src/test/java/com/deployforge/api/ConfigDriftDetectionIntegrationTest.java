package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigDriftDetectionIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void mismatchedConfigVersionCreatesConfigDrift() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:desired");
        setDesiredState(projectId, serviceId, envId, artifactId, "sha256:desired", "config-v2");
        String targetId = registerTarget(mockMvc, projectId, serviceId, envId).get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, projectId, targetId, artifactId, "sha256:desired", "RUNNING");
        configReport(mockMvc, projectId, targetId, "config-v1", "PRESENT");
        JsonNode finding = checkDrift(mockMvc, projectId, serviceId, envId).get("findings").get(0);
        assertThat(finding.get("driftType").asText()).isEqualTo("CONFIG_DRIFT");
        assertThat(finding.get("severity").asText()).isEqualTo("CRITICAL");
    }
}
