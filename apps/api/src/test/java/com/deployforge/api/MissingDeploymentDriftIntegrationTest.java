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
class MissingDeploymentDriftIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void missingReportCreatesMissingDeploymentAndNoReportCreatesUnknownState() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:desired");
        setDesiredState(projectId, serviceId, envId, artifactId, "sha256:desired", null);
        String targetId = registerTarget(mockMvc, projectId, serviceId, envId).get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(checkDrift(mockMvc, projectId, serviceId, envId).get("findings").get(0).get("driftType").asText()).isEqualTo("UNKNOWN_ACTUAL_STATE");
        deploymentReport(mockMvc, projectId, targetId, null, null, "MISSING");
        assertThat(checkDrift(mockMvc, projectId, serviceId, envId).get("findings").get(0).get("driftType").asText()).isEqualTo("MISSING_DEPLOYMENT");
    }
}
