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
class DriftRepairRecommendationIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void manualChangeRecommendsAcceptActualAsDesired() throws Exception {
        JsonNode finding = createBasicDrift(mockMvc).get("findings").get(0);
        assertThat(finding.get("recommendedAction").asText()).isEqualTo("ACCEPT_ACTUAL_AS_DESIRED");
    }
}
