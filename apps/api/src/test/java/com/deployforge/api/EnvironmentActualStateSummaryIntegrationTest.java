package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EnvironmentActualStateSummaryIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void summaryAggregatesOpenDrift() throws Exception {
        JsonNode check = createBasicDrift(mockMvc);
        JsonNode summary = json(mockMvc.perform(get("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/actual-state",
                        check.get("projectId").asText(), check.get("serviceId").asText(), check.get("environmentId").asText()))
                .andExpect(status().isOk()).andReturn());
        assertThat(summary.get("overallActualStatus").asText()).isEqualTo("DRIFTED");
        assertThat(summary.get("recommendedAction").asText()).isEqualTo("REDEPLOY_DESIRED_ARTIFACT");
    }
}
