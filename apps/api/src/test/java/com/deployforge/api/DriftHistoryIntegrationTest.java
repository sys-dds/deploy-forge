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
class DriftHistoryIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void listFilterAndFetchFinding() throws Exception {
        JsonNode check = createBasicDrift(mockMvc);
        String projectId = check.get("projectId").asText();
        String findingId = check.get("findings").get(0).get("driftFindingId").asText();
        assertThat(listDrift(mockMvc, projectId, "OPEN").size()).isEqualTo(1);
        JsonNode fetched = json(mockMvc.perform(get("/api/v1/projects/{projectId}/drift-findings/{findingId}", projectId, findingId))
                .andExpect(status().isOk()).andReturn());
        assertThat(fetched.get("driftFindingId").asText()).isEqualTo(findingId);
    }
}
