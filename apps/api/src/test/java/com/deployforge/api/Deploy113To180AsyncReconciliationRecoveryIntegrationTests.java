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
class CommandCreationIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void createsPendingDurableCommandWithoutExecutingIt() throws Exception {
        String projectId = createProject(mockMvc);
        JsonNode command = command(mockMvc, projectId, uniqueSlug("cmd"), "VERIFY_CONSISTENCY", "{}");
        assertThat(command.get("status").asText()).isEqualTo("PENDING");
        assertThat(command.get("attempts").asInt()).isZero();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandIdempotencyIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void sameKeyAndPayloadReturnsExistingButChangedPayloadConflicts() throws Exception {
        String projectId = createProject(mockMvc);
        String key = uniqueSlug("idem");
        JsonNode first = command(mockMvc, projectId, key, "VERIFY_CONSISTENCY", "{}");
        JsonNode second = command(mockMvc, projectId, key, "VERIFY_CONSISTENCY", "{}");
        assertThat(second.get("commandId").asText()).isEqualTo(first.get("commandId").asText());
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"commandType":"DRIFT_CHECK","payload":{},"createdBy":"operator@example.com","reason":"changed"}
                                """))
                .andExpect(status().isConflict());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RunnerNodeIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void runnerRegisterHeartbeatAndStatusApiWork() throws Exception {
        String projectId = createProject(mockMvc);
        registerRunner(mockMvc, projectId, "runner-local-1");
        json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/heartbeat", projectId, "runner-local-1"))
                .andExpect(status().isOk()).andReturn());
        JsonNode runners = getJson(mockMvc, "/api/v1/projects/{projectId}/runners", projectId);
        assertThat(runners.toString()).contains("runner-local-1", "ACTIVE");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandLeaseIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void claimLeasesCommandAndBlocksSecondRunnerUntilExpiry() throws Exception {
        String projectId = createProject(mockMvc);
        command(mockMvc, projectId, uniqueSlug("lease"), "VERIFY_CONSISTENCY", "{}");
        registerRunner(mockMvc, projectId, "runner-a");
        registerRunner(mockMvc, projectId, "runner-b");
        JsonNode claimed = claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        assertThat(claimed.get("status").asText()).isEqualTo("RUNNING");
        assertThat(claim(mockMvc, projectId, "runner-b", 60, "VERIFY_CONSISTENCY").get("status").asText()).isEqualTo("NO_COMMAND");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandFencingIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void staleFencingTokenCannotCompleteCommand() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("fence"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        JsonNode claimed = claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/{commandId}/complete-success",
                        projectId, "runner-a", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fencingToken":%d,"result":{}}
                                """.formatted(claimed.get("fencingToken").asLong() - 1)))
                .andExpect(status().isConflict());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StaleRunnerTakeoverIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void expiredLeaseCanBeClaimedByAnotherRunner() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("takeover"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        registerRunner(mockMvc, projectId, "runner-b");
        claim(mockMvc, projectId, "runner-a", 1, "VERIFY_CONSISTENCY");
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        JsonNode claimed = claim(mockMvc, projectId, "runner-b", 60, "VERIFY_CONSISTENCY");
        assertThat(claimed.get("leasedByNodeId").asText()).isEqualTo("runner-b");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StaleCompletionRejectionIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void staleCompletionAfterTakeoverIsRejected() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("stale"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        registerRunner(mockMvc, projectId, "runner-b");
        JsonNode a = claim(mockMvc, projectId, "runner-a", 1, "VERIFY_CONSISTENCY");
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        JsonNode b = claim(mockMvc, projectId, "runner-b", 60, "VERIFY_CONSISTENCY");
        mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/{commandId}/complete-success",
                        projectId, "runner-a", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fencingToken":%d,"result":{}}
                                """.formatted(a.get("fencingToken").asLong())))
                .andExpect(status().isConflict());
        succeed(mockMvc, projectId, "runner-b", commandId, b.get("fencingToken").asLong());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandRetryPolicyIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void failureRetriesBeforeParking() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("retry"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        JsonNode claimed = claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        JsonNode failed = fail(mockMvc, projectId, "runner-a", commandId, claimed.get("fencingToken").asLong());
        assertThat(failed.get("status").asText()).isEqualTo("PENDING");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ParkedCommandIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void maxAttemptsParksCommand() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("park"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        JsonNode first = claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        fail(mockMvc, projectId, "runner-a", commandId, first.get("fencingToken").asLong());
        jdbcTemplate.update("update deployment_commands set next_attempt_at = now() where id = ?::uuid", commandId);
        JsonNode second = claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        JsonNode parked = fail(mockMvc, projectId, "runner-a", commandId, second.get("fencingToken").asLong());
        assertThat(parked.get("status").asText()).isEqualTo("PARKED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RequeueParkedCommandIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void requeueReturnsParkedCommandToPending() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("requeue"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        JsonNode first = claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        fail(mockMvc, projectId, "runner-a", commandId, first.get("fencingToken").asLong());
        jdbcTemplate.update("update deployment_commands set next_attempt_at = now() where id = ?::uuid", commandId);
        JsonNode second = claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        fail(mockMvc, projectId, "runner-a", commandId, second.get("fencingToken").asLong());
        JsonNode requeued = json(mockMvc.perform(post("/api/v1/projects/{projectId}/commands/{commandId}/requeue", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requeuedBy":"operator@example.com","reason":"fixed"}
                                """))
                .andExpect(status().isOk()).andReturn());
        assertThat(requeued.get("status").asText()).isEqualTo("PENDING");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandExecutionEventIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void commandLifecycleRecordsEvents() throws Exception {
        String projectId = createProject(mockMvc);
        command(mockMvc, projectId, uniqueSlug("events"), "VERIFY_CONSISTENCY", "{}");
        registerRunner(mockMvc, projectId, "runner-a");
        tick(mockMvc, projectId, "runner-a", "VERIFY_CONSISTENCY");
        JsonNode events = getJson(mockMvc, "/api/v1/projects/{projectId}/deployment-intent-events", projectId);
        assertThat(events.toString()).contains("COMMAND_CREATED", "COMMAND_LEASED", "COMMAND_STARTED", "COMMAND_SUCCEEDED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DriftCheckCommandExecutionIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void runnerExecutesDriftCheckCommand() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        command(mockMvc, projectId, uniqueSlug("driftcmd"), "DRIFT_CHECK", driftPayload(fixture.get("serviceId").asText(),
                fixture.get("environmentId").asText()).toString());
        registerRunner(mockMvc, projectId, "runner-a");
        JsonNode tick = tick(mockMvc, projectId, "runner-a", "DRIFT_CHECK");
        assertThat(tick.get("command").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(listDrift(mockMvc, projectId, "OPEN").toString()).contains("MANUAL_CHANGE");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class VerifyConsistencyCommandIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void runnerExecutesConsistencyVerificationCommand() throws Exception {
        String projectId = createProject(mockMvc);
        command(mockMvc, projectId, uniqueSlug("verify"), "VERIFY_CONSISTENCY", "{}");
        registerRunner(mockMvc, projectId, "runner-a");
        JsonNode tick = tick(mockMvc, projectId, "runner-a", "VERIFY_CONSISTENCY");
        assertThat(tick.toString()).contains("consistent");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class UnsupportedCommandExecutionIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void unsupportedCommandFailsSafelyThroughTick() throws Exception {
        String projectId = createProject(mockMvc);
        command(mockMvc, projectId, uniqueSlug("unsupported"), "ROLLOUT_ADVANCE", "{}");
        registerRunner(mockMvc, projectId, "runner-a");
        JsonNode tick = tick(mockMvc, projectId, "runner-a", "ROLLOUT_ADVANCE");
        assertThat(tick.get("status").asText()).isEqualTo("FAILED");
        assertThat(tick.get("command").get("status").asText()).isEqualTo("PENDING");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RolloutCommandExecutionIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void runnerExecutesRolloutStartCommandThroughDomainService() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        command(mockMvc, ready.projectId(), uniqueSlug("rolloutcmd"), "ROLLOUT_START", """
                {"planId":"%s","startedBy":"release-manager@example.com","reason":"async rollout"}
                """.formatted(ready.planId()));
        registerRunner(mockMvc, ready.projectId(), "runner-a");
        JsonNode result = tick(mockMvc, ready.projectId(), "runner-a", "ROLLOUT_START");
        assertThat(result.get("command").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.toString()).contains(ready.planId());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RollbackCommandExecutionIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void runnerExecutesRollbackStartCommandThroughDomainService() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"),
                "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        command(mockMvc, ready.projectId(), uniqueSlug("rollbackcmd"), "ROLLBACK_START", """
                {"rollbackRecommendationId":"%s","startedBy":"release-manager@example.com","reason":"async rollback"}
                """.formatted(recommendation.get("id").asText()));
        registerRunner(mockMvc, ready.projectId(), "runner-a");
        JsonNode result = tick(mockMvc, ready.projectId(), "runner-a", "ROLLBACK_START");
        assertThat(result.get("command").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.toString()).contains("RUNNING");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandBacklogApiIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void backlogShowsPendingCounts() throws Exception {
        String projectId = createProject(mockMvc);
        command(mockMvc, projectId, uniqueSlug("backlog"), "VERIFY_CONSISTENCY", "{}");
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/commands/backlog", projectId).toString()).contains("PENDING");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationPolicyIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void policyCanBeRecorded() throws Exception {
        String projectId = createProject(mockMvc);
        JsonNode policy = putPolicy(mockMvc, projectId);
        assertThat(policy.get("requireApprovalForRepair").asBoolean()).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationRunIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void reconciliationCreatesIssuesAndRepairPlansFromOpenDrift() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        JsonNode run = reconcile(mockMvc, projectId, uniqueSlug("reconcile"), fixture.get("serviceId").asText(),
                fixture.get("environmentId").asText());
        assertThat(run.get("issues")).hasSizeGreaterThanOrEqualTo(1);
        assertThat(run.get("repairPlans")).hasSizeGreaterThanOrEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RepairPlanApprovalIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void repairPlanApprovalChangesStatus() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        JsonNode run = reconcile(mockMvc, projectId, uniqueSlug("approve"), fixture.get("serviceId").asText(),
                fixture.get("environmentId").asText());
        String planId = run.get("repairPlans").get(0).get("repairPlanId").asText();
        assertThat(approvePlan(mockMvc, projectId, planId).get("status").asText()).isEqualTo("APPROVED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationIdempotencyIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void sameReconciliationKeyReturnsExistingRun() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        String key = uniqueSlug("runidem");
        JsonNode first = reconcile(mockMvc, projectId, key, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        JsonNode second = reconcile(mockMvc, projectId, key, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        assertThat(second.get("run").get("runId").asText()).isEqualTo(first.get("run").get("runId").asText());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationCommandGenerationIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void approvedRepairPlanRecommendationCreatesCommand() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        JsonNode run = reconcile(mockMvc, projectId, uniqueSlug("cmdgen"), fixture.get("serviceId").asText(),
                fixture.get("environmentId").asText());
        String planId = run.get("repairPlans").get(0).get("repairPlanId").asText();
        approvePlan(mockMvc, projectId, planId);
        JsonNode result = json(mockMvc.perform(post("/api/v1/projects/{projectId}/repair-plans/{repairPlanId}/recommend-execution",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedBy":"operator@example.com","reason":"queue repair"}
                                """))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("command").get("commandType").asText()).isEqualTo("CREATE_REPAIR_INTENT");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StuckCommandDetectorIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void stuckCommandDetectorReturnsExpiredLeases() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("stuck"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        claim(mockMvc, projectId, "runner-a", 1, "VERIFY_CONSISTENCY");
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/stuck-commands", projectId).toString()).contains(commandId);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperatorForceParkCommandIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void forceParkRecordsRecoveryAction() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("forcepark"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        JsonNode parked = json(mockMvc.perform(post("/api/v1/projects/{projectId}/ops/commands/{commandId}/force-park", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryRequest().toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(parked.get("status").asText()).isEqualTo("PARKED");
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/recovery-actions", projectId).toString()).contains("FORCE_PARK_COMMAND");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperatorForceRetryCommandIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void forceRetryMovesCommandToPending() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("forceretry"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        jdbcTemplate.update("update deployment_commands set status = 'PARKED' where id = ?::uuid", commandId);
        JsonNode retry = json(mockMvc.perform(post("/api/v1/projects/{projectId}/ops/commands/{commandId}/force-retry", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryRequest().toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(retry.get("status").asText()).isEqualTo("PENDING");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperatorForceReleaseStaleLeaseIntegrationTest extends StuckCommandDetectorIntegrationTest {
    @Test
    void forceReleaseStaleLeaseClearsLease() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("release"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        claim(mockMvc, projectId, "runner-a", 1, "VERIFY_CONSISTENCY");
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        JsonNode released = json(mockMvc.perform(post("/api/v1/projects/{projectId}/ops/leases/{commandId}/force-release-stale",
                        projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryRequest().toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(released.get("status").asText()).isEqualTo("PENDING");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperatorManualResolutionIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void manualResolutionMarksCommandSucceeded() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("manual"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        JsonNode resolved = json(mockMvc.perform(post("/api/v1/projects/{projectId}/ops/commands/{commandId}/mark-manually-resolved",
                        projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryRequest().toString()))
                .andExpect(status().isOk()).andReturn());
        assertThat(resolved.get("status").asText()).isEqualTo("SUCCEEDED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperationalSummaryIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void operationalSummaryReturnsCounts() throws Exception {
        String projectId = createProject(mockMvc);
        command(mockMvc, projectId, uniqueSlug("summary"), "VERIFY_CONSISTENCY", "{}");
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/summary", projectId).toString()).contains("commands");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DeploymentInvestigationSearchIntegrationTest extends OperationalSummaryIntegrationTest {
    @Test
    void investigationReturnsRecentWork() throws Exception {
        String projectId = createProject(mockMvc);
        command(mockMvc, projectId, uniqueSlug("investigate"), "VERIFY_CONSISTENCY", "{}");
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/investigate", projectId).toString()).contains("commands");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StuckRolloutDetectorIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void stuckRolloutDetectorEndpointIsAvailable() throws Exception {
        String projectId = createProject(mockMvc);
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/stuck-rollouts", projectId).isArray()).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StuckRollbackDetectorIntegrationTest extends StuckRolloutDetectorIntegrationTest {
    @Test
    void stuckRollbackDetectorEndpointIsAvailable() throws Exception {
        String projectId = createProject(mockMvc);
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/stuck-rollbacks", projectId).isArray()).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DockerRuntimeDemoScenarioIntegrationTest extends AsyncCommandIntegrationTestSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void applicationRuntimeEndpointsRemainHealthyForDockerScenario() throws Exception {
        json(mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn());
        json(mockMvc.perform(get("/api/v1/system/ping")).andExpect(status().isOk()).andReturn());
        json(mockMvc.perform(get("/api/v1/system/node")).andExpect(status().isOk()).andReturn());
    }
}
