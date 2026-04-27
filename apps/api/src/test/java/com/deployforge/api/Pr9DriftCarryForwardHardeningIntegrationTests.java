package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class Pr9DriftCarryForwardHardeningIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void exactManualChangeFindingFieldsAreRecorded() throws Exception {
        JsonNode drift = createBasicDrift(mockMvc);
        JsonNode finding = drift.get("findings").get(0);
        assertThat(finding.get("driftType").asText()).isEqualTo("MANUAL_CHANGE");
        assertThat(finding.get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(finding.get("recommendedAction").asText()).isEqualTo("ACCEPT_ACTUAL_AS_DESIRED");
        assertThat(finding.get("status").asText()).isEqualTo("OPEN");
        assertThat(finding.get("desired").get("desiredDigest").asText()).isEqualTo("sha256:desired");
        assertThat(finding.get("actual").get("reportedDigest").asText()).isEqualTo("sha256:actual");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DriftClassificationRegressionIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void knownDifferentArtifactIsManualChangeButUnknownDigestIsArtifactDrift() throws Exception {
        JsonNode manual = createBasicDrift(mockMvc);
        assertThat(manual.get("findings").get(0).get("driftType").asText()).isEqualTo("MANUAL_CHANGE");

        String projectId = createProject(mockMvc);
        String serviceId = createCriticalService(mockMvc, projectId);
        String envId = createEnvironment(mockMvc, projectId, "PROD-" + uniqueSlug("env"), "PROD", true, true, 1);
        String desired = registerArtifact(mockMvc, projectId, serviceId, uniqueSlug("v"), "sha256:desired2");
        setDesiredState(projectId, serviceId, envId, desired, "sha256:desired2", null);
        String targetId = registerTarget(mockMvc, projectId, serviceId, envId).get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, projectId, targetId, null, "sha256:unknown", "RUNNING");
        JsonNode artifact = checkDrift(mockMvc, projectId, serviceId, envId);
        assertThat(artifact.get("findings").get(0).get("driftType").asText()).isEqualTo("ARTIFACT_DRIFT");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DriftDuplicateFindingRegressionIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void repeatedChecksUpdateExistingOpenFinding() throws Exception {
        JsonNode first = createBasicDrift(mockMvc);
        JsonNode finding = first.get("findings").get(0);
        String projectId = finding.get("projectId").asText();
        String serviceId = finding.get("serviceId").asText();
        String environmentId = finding.get("environmentId").asText();
        checkDrift(mockMvc, projectId, serviceId, environmentId);
        JsonNode open = listDrift(mockMvc, projectId, "OPEN");
        assertThat(open).hasSize(1);
        assertThat(open.get(0).get("driftFindingId").asText()).isEqualTo(finding.get("driftFindingId").asText());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DriftResolutionRecheckRegressionIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void resolvedDriftStillMismatchedCreatesNewOpenFindingOnNextCheck() throws Exception {
        JsonNode drift = createBasicDrift(mockMvc);
        JsonNode finding = drift.get("findings").get(0);
        String projectId = finding.get("projectId").asText();
        String findingId = finding.get("driftFindingId").asText();
        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/resolve", projectId, findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolvedBy":"operator@example.com","reason":"explicit close"}
                                """))
                .andExpect(status().isOk()).andReturn());
        checkDrift(mockMvc, projectId, finding.get("serviceId").asText(), finding.get("environmentId").asText());
        JsonNode open = listDrift(mockMvc, projectId, "OPEN");
        assertThat(open).hasSize(1);
        assertThat(open.get(0).get("driftFindingId").asText()).isNotEqualTo(findingId);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DriftAwareReadinessRegressionIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void criticalOpenAndAcknowledgedDriftBlockButResolvedDriftDoesNot() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String targetId = registerTarget(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId()).get("runtimeTargetId").asText();
        heartbeat(mockMvc, ready.projectId(), targetId, OffsetDateTime.now(ZoneOffset.UTC));
        String actual = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("actual"), "sha256:actual-readiness");
        setDesiredState(ready.projectId(), ready.serviceId(), ready.prodId(), ready.artifactId(), "sha256:desired-readiness", null);
        deploymentReport(mockMvc, ready.projectId(), targetId, actual, "sha256:actual-readiness", "RUNNING");
        JsonNode drift = checkDrift(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId());
        String findingId = drift.get("findings").get(0).get("driftFindingId").asText();
        JsonNode blocked = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness",
                        ready.projectId(), ready.planId()))
                .andExpect(status().isOk()).andReturn());
        assertThat(blocked.get("readyToStart").asBoolean()).isFalse();

        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/acknowledge", ready.projectId(), findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"acknowledgedBy":"operator@example.com","reason":"known"}
                                """))
                .andExpect(status().isOk()).andReturn());
        JsonNode acknowledged = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness",
                        ready.projectId(), ready.planId()))
                .andExpect(status().isOk()).andReturn());
        assertThat(acknowledged.get("readyToStart").asBoolean()).isFalse();

        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/resolve", ready.projectId(), findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolvedBy":"operator@example.com","reason":"closed"}
                                """))
                .andExpect(status().isOk()).andReturn());
        JsonNode clear = json(mockMvc.perform(get("/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness",
                        ready.projectId(), ready.planId()))
                .andExpect(status().isOk()).andReturn());
        assertThat(clear.get("readyToStart").asBoolean()).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RollbackDriftRegressionIntegrationTest extends DriftIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void rollbackSuccessDesiredStateAndRuntimeMismatchPolicyAreExplicit() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"), "sha256:stable-regression");
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        String rollbackId = startRollback(mockMvc, ready.projectId(), recommendation.get("id").asText()).get("id").asText();
        evaluateRollbackGates(mockMvc, ready.projectId(), rollbackId, ready.syntheticGateId(), ready.metricGateId(), 0.1);
        completeRollbackSuccess(mockMvc, ready.projectId(), rollbackId);
        assertThat(desiredState(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId()).get("desiredArtifactId").asText())
                .isEqualTo(stableArtifactId);

        String targetId = registerTarget(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId()).get("runtimeTargetId").asText();
        heartbeat(mockMvc, ready.projectId(), targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, ready.projectId(), targetId, ready.artifactId(), "sha256:failed-runtime", "RUNNING");
        JsonNode drift = checkDrift(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId());
        assertThat(drift.toString()).contains("MANUAL_CHANGE");

        deploymentReport(mockMvc, ready.projectId(), targetId, stableArtifactId, "sha256:stable-regression", "RUNNING");
        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{findingId}/resolve",
                        ready.projectId(), drift.get("findings").get(0).get("driftFindingId").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resolvedBy":"operator@example.com","reason":"runtime matched rollback target"}
                                """))
                .andExpect(status().isOk()).andReturn());
        JsonNode clean = checkDrift(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId());
        assertThat(clean.get("findings")).isEmpty();
    }
}
