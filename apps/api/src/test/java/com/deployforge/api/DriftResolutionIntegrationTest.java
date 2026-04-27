package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DriftResolutionIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void resolveOpenDriftAndExcludeFromOpenList() throws Exception {
        JsonNode check = createBasicDrift(mockMvc);
        String projectId = check.get("projectId").asText();
        String findingId = check.get("findings").get(0).get("driftFindingId").asText();
        JsonNode resolved = json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/resolve", projectId, findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolvedBy":"operator@example.com","reason":"Runtime fixed"}
                                """))
                .andExpect(status().isOk()).andReturn());
        assertThat(resolved.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(listDrift(mockMvc, projectId, "OPEN").size()).isZero();
    }
}
