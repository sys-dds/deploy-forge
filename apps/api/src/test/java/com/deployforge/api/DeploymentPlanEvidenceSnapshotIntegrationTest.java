package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DeploymentPlanEvidenceSnapshotIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void snapshotIsCapturedAndNotMutatedByLaterEvidence() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "snapshot-staging", "STAGING", false, false, 3);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, "8.0.0", "sha256:snapshot");
        addEvidence(mockMvc, projectId, serviceId, artifactId, "TEST_REPORT");

        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE", uniqueSlug("snapshot-key"));
        assertThat(plan.at("/evidenceSnapshot/artifact/version").asText()).isEqualTo("8.0.0");
        assertThat(plan.at("/evidenceSnapshot/risk/riskLevel").asText()).isEqualTo("MEDIUM");
        assertThat(plan.at("/evidenceSnapshot/evidenceRefs").size()).isEqualTo(1);

        addEvidence(mockMvc, projectId, serviceId, artifactId, "IMAGE_SCAN");
        JsonNode replay = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "ALL_AT_ONCE",
                plan.get("idempotencyKey").asText());
        assertThat(replay.at("/evidenceSnapshot/evidenceRefs").size()).isEqualTo(1);
    }
}
