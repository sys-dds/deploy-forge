package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.deployforge.api.drift.DriftSeverityCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DriftSeverityCalculatorIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;
    @Autowired DriftSeverityCalculator calculator;

    @Test
    void protectedProdRaisesArtifactAndStaleSeverity() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 1);
        assertThat(calculator.severity(java.util.UUID.fromString(envId), java.util.UUID.fromString(serviceId), "ARTIFACT_DRIFT")).isEqualTo("CRITICAL");
        assertThat(calculator.severity(java.util.UUID.fromString(envId), java.util.UUID.fromString(serviceId), "STALE_TARGET_REPORT")).isEqualTo("CRITICAL");
    }
}
