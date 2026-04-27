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
class VerifierDepthHardeningIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void detectsEnvironmentCurrentArtifactOutsideService() throws Exception {
        String projectId = createProject(mockMvc);
        String serviceA = createService(mockMvc, projectId);
        String serviceB = createService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "DEV-" + uniqueSlug("env"), "DEV", false, false, 1);
        String foreignArtifact = registerArtifact(mockMvc, projectId, serviceB, uniqueSlug("v"), "sha256:foreign");
        jdbcTemplate.update("""
                insert into environment_deployment_states (id, project_id, service_id, environment_id, current_artifact_id, state_status)
                values (gen_random_uuid(), ?::uuid, ?::uuid, ?::uuid, ?::uuid, 'DEPLOYED')
                """, projectId, serviceA, envId, foreignArtifact);

        JsonNode verify = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-consistency", projectId))
                .andExpect(status().isOk()).andReturn());
        assertThat(verify.toString()).contains("ENVIRONMENT_ARTIFACT_SCOPE_MISMATCH");
    }
}
