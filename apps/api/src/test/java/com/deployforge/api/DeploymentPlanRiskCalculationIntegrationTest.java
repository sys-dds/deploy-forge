package com.deployforge.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class DeploymentPlanRiskCalculationIntegrationTest extends CoreReleaseIntentTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void riskLevelsAndReasonsAreCaptured() throws Exception {
        String projectId = createProject(mockMvc);
        String standardService = createService(mockMvc, projectId);
        String criticalService = createCriticalService(mockMvc, projectId);
        String dev = createEnvironment(mockMvc, projectId, "risk-dev", "DEV", false, false, 1);
        String staging = createEnvironment(mockMvc, projectId, "risk-staging", "STAGING", false, false, 3);
        String prod = createEnvironment(mockMvc, projectId, "risk-prod", "PROD", true, true, 4);
        String standardArtifact = registerArtifact(mockMvc, projectId, standardService, "7.0.0", "sha256:risk-standard");
        String criticalArtifact = registerArtifact(mockMvc, projectId, criticalService, "7.0.1", "sha256:risk-critical");
        addEvidence(mockMvc, projectId, criticalService, criticalArtifact, "TEST_REPORT");
        addEvidence(mockMvc, projectId, criticalService, criticalArtifact, "IMAGE_SCAN");

        Assertions.assertThat(createPlan(mockMvc, projectId, standardService, standardArtifact, dev,
                "ALL_AT_ONCE", uniqueSlug("risk-dev-key")).get("riskLevel").asText()).isEqualTo("LOW");
        Assertions.assertThat(createPlan(mockMvc, projectId, standardService, standardArtifact, staging,
                "ALL_AT_ONCE", uniqueSlug("risk-staging-key")).get("riskLevel").asText()).isEqualTo("MEDIUM");
        Assertions.assertThat(createPlan(mockMvc, projectId, standardService, standardArtifact, staging,
                "CANARY", uniqueSlug("risk-canary-key")).get("riskLevel").asText()).isEqualTo("MEDIUM");
        Assertions.assertThat(createPlan(mockMvc, projectId, criticalService, criticalArtifact, prod,
                "CANARY", uniqueSlug("risk-prod-key")).get("riskLevel").asText()).isEqualTo("CRITICAL");
    }
}
