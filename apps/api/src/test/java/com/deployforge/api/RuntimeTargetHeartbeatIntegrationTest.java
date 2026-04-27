package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RuntimeTargetHeartbeatIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void recordsLatestHeartbeatAndFreshness() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 1);
        String targetId = registerTarget(mockMvc, projectId, serviceId, envId).get("runtimeTargetId").asText();
        assertThat(heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC)).get("freshness").asText()).isEqualTo("FRESH");
        assertThat(heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10)).get("freshness").asText()).isEqualTo("STALE");
    }
}
