package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DriftAwareReadinessIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void criticalOpenDriftBlocksReadiness() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        setDesiredState(ready.projectId(), ready.serviceId(), ready.prodId(), ready.artifactId(), "sha256:desired", null);
        String targetId = registerTarget(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId()).get("runtimeTargetId").asText();
        heartbeat(mockMvc, ready.projectId(), targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, ready.projectId(), targetId, null, "sha256:actual", "RUNNING");
        checkDrift(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId());

        JsonNode readiness = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness",
                        ready.projectId(), ready.planId()))
                .andExpect(status().isOk()).andReturn());
        assertThat(readiness.get("readyToStart").asBoolean()).isFalse();
        assertThat(readiness.toString()).contains("CRITICAL_DRIFT_PRESENT");
    }
}
