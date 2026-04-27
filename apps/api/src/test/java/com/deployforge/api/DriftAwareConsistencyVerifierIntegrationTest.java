package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class DriftAwareConsistencyVerifierIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void mismatchWithoutOpenDriftIsDetectedAndOpenDriftExplainsIt() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:desired");
        setDesiredState(projectId, serviceId, envId, artifactId, "sha256:desired", null);
        String targetId = registerTarget(mockMvc, projectId, serviceId, envId).get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, projectId, targetId, null, "sha256:wrong", "RUNNING");
        JsonNode before = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-consistency", projectId))
                .andExpect(status().isOk()).andReturn());
        assertThat(before.toString()).contains("ACTUAL_MISMATCH_WITHOUT_OPEN_DRIFT");
        checkDrift(mockMvc, projectId, serviceId, envId);
        JsonNode after = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-consistency", projectId))
                .andExpect(status().isOk()).andReturn());
        assertThat(after.toString()).doesNotContain("ACTUAL_MISMATCH_WITHOUT_OPEN_DRIFT");
    }
}
