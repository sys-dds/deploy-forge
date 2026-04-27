package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ManualOutOfBandChangeIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void knownDifferentArtifactIsManualChange() throws Exception {
        JsonNode finding = createBasicDrift(mockMvc).get("findings").get(0);
        assertThat(finding.get("driftType").asText()).isEqualTo("MANUAL_CHANGE");
        assertThat(finding.get("recommendedAction").asText()).isEqualTo("ACCEPT_ACTUAL_AS_DESIRED");
        assertThat(finding.get("severity").asText()).isEqualTo("CRITICAL");
    }
}
