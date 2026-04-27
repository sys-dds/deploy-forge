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
class DriftAcknowledgementIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void acknowledgeOpenDriftWithoutResolvingIt() throws Exception {
        JsonNode check = createBasicDrift(mockMvc);
        String projectId = check.get("projectId").asText();
        String findingId = check.get("findings").get(0).get("driftFindingId").asText();
        JsonNode ack = json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/acknowledge", projectId, findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acknowledgedBy":"operator@example.com","reason":"Known drift"}
                                """))
                .andExpect(status().isOk()).andReturn());
        assertThat(ack.get("status").asText()).isEqualTo("ACKNOWLEDGED");
    }
}
