package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DriftFailureMatrixIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void artifactConfigMissingManualAndStaleCasesAreDeterministic() throws Exception {
        assertThat(createBasicDrift(mockMvc).get("findings").get(0).get("driftType").asText()).isEqualTo("MANUAL_CHANGE");

        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:desired");
        setDesiredState(projectId, serviceId, envId, artifactId, "sha256:desired", "config-v2");
        String targetId = registerTarget(mockMvc, projectId, serviceId, envId).get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, projectId, targetId, artifactId, "sha256:desired", "RUNNING");
        configReport(mockMvc, projectId, targetId, "config-v1", "PRESENT");
        assertThat(checkDrift(mockMvc, projectId, serviceId, envId).toString()).contains("CONFIG_DRIFT");

        String staleProject = createProject(mockMvc);
        String staleService = createCriticalService(mockMvc, staleProject);
        String staleEnv = createEnvironment(mockMvc, staleProject, "PROD-" + uniqueSlug("env"), "PROD", true, true, 1);
        String staleArtifact = registerArtifact(mockMvc, staleProject, staleService, uniqueSlug("v"), "sha256:desired");
        setDesiredState(staleProject, staleService, staleEnv, staleArtifact, "sha256:desired", null);
        String staleTarget = registerTarget(mockMvc, staleProject, staleService, staleEnv).get("runtimeTargetId").asText();
        heartbeat(mockMvc, staleProject, staleTarget, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        assertThat(checkDrift(mockMvc, staleProject, staleService, staleEnv).toString()).contains("STALE_TARGET_REPORT");
    }
}
