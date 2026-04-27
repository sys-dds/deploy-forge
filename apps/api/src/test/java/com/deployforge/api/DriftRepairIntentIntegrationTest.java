package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class DriftRepairIntentIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void createAndListRepairIntent() throws Exception {
        JsonNode check = createBasicDrift(mockMvc);
        String projectId = check.get("projectId").asText();
        String findingId = check.get("findings").get(0).get("driftFindingId").asText();
        JsonNode intent = json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/repair-intents", projectId, findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intentType":"REDEPLOY_DESIRED_ARTIFACT","requestedBy":"operator@example.com","reason":"Restore desired","metadata":{"ticket":"INC-123"}}
                                """))
                .andExpect(status().isCreated()).andReturn());
        assertThat(intent.get("intentType").asText()).isEqualTo("REDEPLOY_DESIRED_ARTIFACT");
        assertThat(json(mockMvc.perform(get("/api/v1/projects/{projectId}/drift-findings/{findingId}/repair-intents", projectId, findingId))
                .andExpect(status().isOk()).andReturn()).size()).isEqualTo(1);
    }
}
