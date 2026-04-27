package com.deployforge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class ApprovalLifecycleRegressionIntegrationTest extends RolloutIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void duplicateApproverAndDecisionAfterFinalStatusAreRejected() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 1);
        String artifactId = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:" + uniqueSlug("artifact"));
        JsonNode plan = createPlan(mockMvc, projectId, serviceId, artifactId, envId, "CANARY", uniqueSlug("plan"));
        String approvalId = json(mockMvc.perform(post("/api/v1/projects/{projectId}/deployment-plans/{planId}/approval-requests", projectId, plan.get("id").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedBy\":\"dev@example.com\",\"reason\":\"Approve\",\"requiredApprovalCount\":2}"))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        String decision = "{\"decision\":\"APPROVE\",\"decidedBy\":\"approver@example.com\",\"reason\":\"Reviewed\"}";
        mockMvc.perform(post("/api/v1/projects/{projectId}/approval-requests/{approvalRequestId}/decisions", projectId, approvalId)
                        .contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/{projectId}/approval-requests/{approvalRequestId}/decisions", projectId, approvalId)
                        .contentType(MediaType.APPLICATION_JSON).content(decision))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/projects/{projectId}/approval-requests/{approvalRequestId}/decisions", projectId, approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"decidedBy\":\"rejector@example.com\",\"reason\":\"No\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/{projectId}/approval-requests/{approvalRequestId}/decisions", projectId, approvalId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"decidedBy\":\"late@example.com\",\"reason\":\"Late\"}"))
                .andExpect(status().isConflict());
    }
}
