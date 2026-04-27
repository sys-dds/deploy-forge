package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RuntimeTargetRegistryIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void registerListAndRejectChangedDuplicateTarget() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceId = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 1);
        String targetId = registerTarget(mockMvc, projectId, serviceId, envId).get("runtimeTargetId").asText();
        assertThat(targets(mockMvc, projectId, serviceId, envId).toString()).contains(targetId);

        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/runtime-targets", projectId, serviceId, envId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetKey":"fixed","targetType":"SIMULATED_RUNTIME","displayName":"A","registeredBy":"operator@example.com","metadata":{}}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects/{projectId}/services/{serviceId}/environments/{environmentId}/runtime-targets", projectId, serviceId, envId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetKey":"fixed","targetType":"VM_GROUP","displayName":"B","registeredBy":"operator@example.com","metadata":{}}
                                """))
                .andExpect(status().isConflict());
    }
}
