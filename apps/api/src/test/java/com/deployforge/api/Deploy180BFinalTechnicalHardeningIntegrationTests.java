package com.deployforge.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

abstract class Deploy180BProofSupport extends AsyncCommandIntegrationTestSupport {
    JsonNode command(MockMvc mockMvc, String projectId, String key, String type, String payload, int maxAttempts) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/commands", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandType": "%s",
                                  "payload": %s,
                                  "priority": 100,
                                  "maxAttempts": %d,
                                  "createdBy": "operator@example.com",
                                  "reason": "hardening command"
                                }
                                """.formatted(type, payload, maxAttempts)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode commandWithPriority(MockMvc mockMvc, String projectId, String key, String type, int priority) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/commands", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "commandType": "%s",
                                  "payload": {},
                                  "priority": %d,
                                  "maxAttempts": 2,
                                  "createdBy": "operator@example.com",
                                  "reason": "priority command"
                                }
                                """.formatted(type, priority)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    JsonNode claimTypes(MockMvc mockMvc, String projectId, String nodeId, int leaseSeconds, String typesJson) throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/claim", projectId, nodeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"leaseSeconds":%d,"supportedCommandTypes":%s}
                                """.formatted(leaseSeconds, typesJson)))
                .andExpect(status().isOk())
                .andReturn());
    }

    JsonNode completeFailure(MockMvc mockMvc, String projectId, String nodeId, String commandId, long token, String error)
            throws Exception {
        return json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/{commandId}/complete-failure",
                        projectId, nodeId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fencingToken":%d,"errorMessage":"%s"}
                                """.formatted(token, error)))
                .andExpect(status().isOk())
                .andReturn());
    }

    int eventCount(String projectId, String eventType, String relatedId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from deployment_intent_events
                where project_id = ?::uuid and event_type = ? and metadata_json::text like ?
                """, Integer.class, projectId, eventType, "%" + relatedId + "%");
    }

    String commandStatus(String commandId) {
        return jdbcTemplate.queryForObject("select status from deployment_commands where id = ?::uuid", String.class, commandId);
    }

    int attemptCount(String commandId) {
        return jdbcTemplate.queryForObject("select count(*) from deployment_command_attempts where command_id = ?::uuid",
                Integer.class, commandId);
    }

    String attemptStatuses(String commandId) {
        return jdbcTemplate.queryForObject("""
                select string_agg(status, ',' order by attempt_number)
                from deployment_command_attempts
                where command_id = ?::uuid
                """, String.class, commandId);
    }

    String latestResult(String commandId) {
        return jdbcTemplate.queryForObject("""
                select result_json::text
                from deployment_command_attempts
                where command_id = ?::uuid
                order by attempt_number desc limit 1
                """, String.class, commandId);
    }

    JsonNode readyRollbackFixture(MockMvc mockMvc) throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String stableArtifactId = registerArtifact(mockMvc, ready.projectId(), ready.serviceId(), uniqueSlug("stable"),
                "sha256:" + uniqueSlug("stable"));
        setCurrentArtifact(jdbcTemplate, ready.projectId(), ready.serviceId(), ready.prodId(), stableArtifactId, ready.planId());
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode recommendation = failSecondWave(mockMvc, ready, rolloutId);
        return com.deployforge.api.shared.Jsonb.object()
                .put("projectId", ready.projectId())
                .put("serviceId", ready.serviceId())
                .put("environmentId", ready.prodId())
                .put("planId", ready.planId())
                .put("failedArtifactId", ready.artifactId())
                .put("stableArtifactId", stableArtifactId)
                .put("rolloutId", rolloutId)
                .put("recommendationId", recommendation.get("id").asText())
                .put("syntheticGateId", ready.syntheticGateId())
                .put("metricGateId", ready.metricGateId());
    }

    JsonNode force(MockMvc mockMvc, String path, String projectId, String commandId) throws Exception {
        return json(mockMvc.perform(post(path, projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryRequest().toString()))
                .andExpect(status().isOk())
                .andReturn());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandCreationHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void creationRequiresIdempotencyStartsPendingAndDoesNotExecute() throws Exception {
        String projectId = createProject(mockMvc);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"VERIFY_CONSISTENCY\",\"payload\":{},\"createdBy\":\"operator@example.com\",\"reason\":\"missing key\"}"))
                .andExpect(status().isBadRequest());
        JsonNode created = command(mockMvc, projectId, uniqueSlug("create"), "VERIFY_CONSISTENCY", "{}");
        assertThat(created.get("status").asText()).isEqualTo("PENDING");
        assertThat(created.get("attempts").asInt()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from deployment_command_attempts where command_id = ?::uuid",
                Integer.class, created.get("commandId").asText())).isZero();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandIdempotencyHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void sameKeyReturnsExistingChangedPayloadConflictsAndHashIsStable() throws Exception {
        String projectId = createProject(mockMvc);
        String key = uniqueSlug("idem180b");
        JsonNode first = command(mockMvc, projectId, key, "VERIFY_CONSISTENCY", "{}");
        JsonNode replay = command(mockMvc, projectId, key, "VERIFY_CONSISTENCY", "{}");
        assertThat(replay.get("commandId").asText()).isEqualTo(first.get("commandId").asText());
        assertThat(replay.get("requestHash").asText()).isEqualTo(first.get("requestHash").asText());
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandType\":\"DRIFT_CHECK\",\"payload\":{},\"createdBy\":\"operator@example.com\",\"reason\":\"changed\"}"))
                .andExpect(status().isConflict());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandStatusTransitionIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void commandMovesPendingRunningRetryParkedRequeueSucceeded() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("status"), "VERIFY_CONSISTENCY", "{}", 2).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-status");
        JsonNode firstClaim = claim(mockMvc, projectId, "runner-status", 60, "VERIFY_CONSISTENCY");
        assertThat(firstClaim.get("status").asText()).isEqualTo("RUNNING");
        JsonNode retry = completeFailure(mockMvc, projectId, "runner-status", commandId, firstClaim.get("fencingToken").asLong(), "retry me");
        assertThat(retry.get("status").asText()).isEqualTo("PENDING");
        jdbcTemplate.update("update deployment_commands set next_attempt_at = now() where id = ?::uuid", commandId);
        JsonNode secondClaim = claim(mockMvc, projectId, "runner-status", 60, "VERIFY_CONSISTENCY");
        JsonNode parked = completeFailure(mockMvc, projectId, "runner-status", commandId, secondClaim.get("fencingToken").asLong(), "park me");
        assertThat(parked.get("status").asText()).isEqualTo("PARKED");
        JsonNode requeued = json(mockMvc.perform(post("/api/v1/projects/{projectId}/commands/{commandId}/requeue", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requeuedBy\":\"operator@example.com\",\"reason\":\"retry after fix\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(requeued.get("status").asText()).isEqualTo("PENDING");
        JsonNode finalClaim = claim(mockMvc, projectId, "runner-status", 60, "VERIFY_CONSISTENCY");
        assertThat(succeed(mockMvc, projectId, "runner-status", commandId, finalClaim.get("fencingToken").asLong())
                .get("status").asText()).isEqualTo("SUCCEEDED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandEventIdempotencyIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void idempotentReplayDoesNotDuplicateCommandCreatedEvent() throws Exception {
        String projectId = createProject(mockMvc);
        String key = uniqueSlug("event-idem");
        String commandId = command(mockMvc, projectId, key, "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        command(mockMvc, projectId, key, "VERIFY_CONSISTENCY", "{}");
        assertThat(eventCount(projectId, "COMMAND_CREATED", commandId)).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RunnerRegistrationHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void runnerRegistrationIsIdempotentHeartbeatRefreshesAndStatusCanBecomeStale() throws Exception {
        String projectId = createProject(mockMvc);
        JsonNode first = registerRunner(mockMvc, projectId, "runner-reg");
        JsonNode second = registerRunner(mockMvc, projectId, "runner-reg");
        assertThat(second.get("runnerId").asText()).isEqualTo(first.get("runnerId").asText());
        jdbcTemplate.update("update runner_nodes set last_seen_at = now() - interval '10 minutes' where node_id = 'runner-reg'");
        JsonNode runners = getJson(mockMvc, "/api/v1/projects/{projectId}/runners", projectId);
        assertThat(runners.toString()).contains("runner-reg").contains("STALE");
        json(mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/heartbeat", projectId, "runner-reg"))
                .andExpect(status().isOk()).andReturn());
        assertThat(jdbcTemplate.queryForObject("select status from runner_nodes where node_id = 'runner-reg'", String.class))
                .isEqualTo("ACTIVE");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandClaimSelectionIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void claimFiltersSupportedTypesAndUsesPriorityThenCreatedOrder() throws Exception {
        String projectId = createProject(mockMvc);
        String lowPriorityId = commandWithPriority(mockMvc, projectId, uniqueSlug("low"), "VERIFY_CONSISTENCY", 200)
                .get("commandId").asText();
        String highPriorityId = commandWithPriority(mockMvc, projectId, uniqueSlug("high"), "DRIFT_CHECK", 5).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-claim");
        assertThat(claimTypes(mockMvc, projectId, "runner-claim", 60, "[\"VERIFY_CONSISTENCY\"]").get("commandId").asText())
                .isEqualTo(lowPriorityId);
        jdbcTemplate.update("update deployment_commands set status = 'PENDING', leased_by_node_id = null, lease_expires_at = null where id = ?::uuid", lowPriorityId);
        assertThat(claimTypes(mockMvc, projectId, "runner-claim", 60, "[\"VERIFY_CONSISTENCY\",\"DRIFT_CHECK\"]").get("commandId").asText())
                .isEqualTo(highPriorityId);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandLeaseExpiryIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void nonExpiredLeaseBlocksTakeoverButExpiredLeaseAllowsNewAttempt() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("lease-expiry"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        registerRunner(mockMvc, projectId, "runner-b");
        claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        assertThat(claim(mockMvc, projectId, "runner-b", 60, "VERIFY_CONSISTENCY").get("status").asText()).isEqualTo("NO_COMMAND");
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        assertThat(claim(mockMvc, projectId, "runner-b", 60, "VERIFY_CONSISTENCY").get("leasedByNodeId").asText()).isEqualTo("runner-b");
        assertThat(attemptCount(commandId)).isEqualTo(2);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandFencingTokenIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void fencingTokenIncrementsAndOldTokenIsRejected() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("fence-token"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        JsonNode claimed = claim(mockMvc, projectId, "runner-a", 60, "VERIFY_CONSISTENCY");
        assertThat(claimed.get("fencingToken").asLong()).isEqualTo(2);
        mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/{commandId}/complete-success",
                        projectId, "runner-a", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fencingToken\":1,\"result\":{}}"))
                .andExpect(status().isConflict());
        assertThat(eventCount(projectId, "COMMAND_STALE_COMPLETION_REJECTED", commandId)).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StaleRunnerTakeoverHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void staleRunnerTakeoverCreatesSecondAttemptAndFreshRunnerCompletes() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("takeover-hard"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        registerRunner(mockMvc, projectId, "runner-b");
        claim(mockMvc, projectId, "runner-a", 1, "VERIFY_CONSISTENCY");
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        JsonNode taken = claim(mockMvc, projectId, "runner-b", 60, "VERIFY_CONSISTENCY");
        assertThat(taken.get("attempts").asInt()).isEqualTo(2);
        assertThat(succeed(mockMvc, projectId, "runner-b", commandId, taken.get("fencingToken").asLong()).get("status").asText())
                .isEqualTo("SUCCEEDED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StaleCompletionRejectionHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void oldRunnerCompletionAfterTakeoverIsRejectedAndCommandSucceedsOnce() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("stale-complete"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        registerRunner(mockMvc, projectId, "runner-b");
        JsonNode first = claim(mockMvc, projectId, "runner-a", 1, "VERIFY_CONSISTENCY");
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        JsonNode second = claim(mockMvc, projectId, "runner-b", 60, "VERIFY_CONSISTENCY");
        mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/{commandId}/complete-success",
                        projectId, "runner-a", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fencingToken\":%d,\"result\":{}}".formatted(first.get("fencingToken").asLong())))
                .andExpect(status().isConflict());
        succeed(mockMvc, projectId, "runner-b", commandId, second.get("fencingToken").asLong());
        assertThat(commandStatus(commandId)).isEqualTo("SUCCEEDED");
        assertThat(attemptStatuses(commandId)).contains("STALE_REJECTED").contains("SUCCEEDED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandRetryBackoffIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void failedCommandBacksOffAndCannotBeClaimedUntilNextAttempt() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("backoff"), "VERIFY_CONSISTENCY", "{}", 2).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-backoff");
        JsonNode claimed = claim(mockMvc, projectId, "runner-backoff", 60, "VERIFY_CONSISTENCY");
        JsonNode failed = completeFailure(mockMvc, projectId, "runner-backoff", commandId, claimed.get("fencingToken").asLong(), "transient");
        assertThat(failed.get("status").asText()).isEqualTo("PENDING");
        assertThat(OffsetDateTime.parse(failed.get("nextAttemptAt").asText()).isAfter(OffsetDateTime.now(ZoneOffset.UTC))).isTrue();
        assertThat(claim(mockMvc, projectId, "runner-backoff", 60, "VERIFY_CONSISTENCY").get("status").asText()).isEqualTo("NO_COMMAND");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandParkingHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void maxAttemptsParksCommandAndRecordsParkedRowAndEvent() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("park-hard"), "VERIFY_CONSISTENCY", "{}", 1).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-park-hard");
        JsonNode claimed = claim(mockMvc, projectId, "runner-park-hard", 60, "VERIFY_CONSISTENCY");
        JsonNode parked = completeFailure(mockMvc, projectId, "runner-park-hard", commandId, claimed.get("fencingToken").asLong(), "fatal");
        assertThat(parked.get("status").asText()).isEqualTo("PARKED");
        assertThat(jdbcTemplate.queryForObject("select reason from parked_deployment_commands where command_id = ?::uuid",
                String.class, commandId)).isEqualTo("fatal");
        assertThat(eventCount(projectId, "COMMAND_PARKED", commandId)).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ParkedCommandNotClaimableIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void parkedCommandIsNotClaimableUntilRequeued() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("not-claimable"), "VERIFY_CONSISTENCY", "{}", 1).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-not-claimable");
        JsonNode claimed = claim(mockMvc, projectId, "runner-not-claimable", 60, "VERIFY_CONSISTENCY");
        completeFailure(mockMvc, projectId, "runner-not-claimable", commandId, claimed.get("fencingToken").asLong(), "fatal");
        assertThat(claim(mockMvc, projectId, "runner-not-claimable", 60, "VERIFY_CONSISTENCY").get("status").asText())
                .isEqualTo("NO_COMMAND");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RequeueParkedCommandHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void requeueOnlyParkedCommandClearsLeaseAndRecordsAudit() throws Exception {
        String projectId = createProject(mockMvc);
        String pendingId = command(mockMvc, projectId, uniqueSlug("pending-requeue"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/{commandId}/requeue", projectId, pendingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requeuedBy\":\"operator@example.com\",\"reason\":\"not parked\"}"))
                .andExpect(status().isConflict());
        jdbcTemplate.update("update deployment_commands set status = 'CANCELLED' where id = ?::uuid", pendingId);
        String commandId = command(mockMvc, projectId, uniqueSlug("requeue-hard"), "VERIFY_CONSISTENCY", "{}", 1).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-requeue-hard");
        JsonNode claimed = claim(mockMvc, projectId, "runner-requeue-hard", 60, "VERIFY_CONSISTENCY");
        completeFailure(mockMvc, projectId, "runner-requeue-hard", commandId, claimed.get("fencingToken").asLong(), "fatal");
        JsonNode requeued = json(mockMvc.perform(post("/api/v1/projects/{projectId}/commands/{commandId}/requeue", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requeuedBy\":\"operator@example.com\",\"reason\":\"fixed\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(requeued.get("status").asText()).isEqualTo("PENDING");
        assertThat(requeued.get("leasedByNodeId").isNull()).isTrue();
        assertThat(eventCount(projectId, "COMMAND_REQUEUED", commandId)).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ParkedToSuccessFunctionalIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void requeuedParkedCommandCanExecuteSuccessfully() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("park-success"), "VERIFY_CONSISTENCY", "{}", 1).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-park-success");
        JsonNode first = claim(mockMvc, projectId, "runner-park-success", 60, "VERIFY_CONSISTENCY");
        completeFailure(mockMvc, projectId, "runner-park-success", commandId, first.get("fencingToken").asLong(), "fatal");
        json(mockMvc.perform(post("/api/v1/projects/{projectId}/commands/{commandId}/requeue", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requeuedBy\":\"operator@example.com\",\"reason\":\"fixed\"}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode second = claim(mockMvc, projectId, "runner-park-success", 60, "VERIFY_CONSISTENCY");
        assertThat(succeed(mockMvc, projectId, "runner-park-success", commandId, second.get("fencingToken").asLong())
                .get("status").asText()).isEqualTo("SUCCEEDED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class VerifyConsistencyCommandExecutionHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void verifyCommandExecutesVerifierAndPersistsResult() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("verify-hard"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-verify-hard");
        JsonNode ticked = tick(mockMvc, projectId, "runner-verify-hard", "VERIFY_CONSISTENCY");
        assertThat(ticked.get("command").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(latestResult(commandId)).contains("\"consistent\": true");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DriftCheckCommandExecutionHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void driftCommandCreatesFindingAndPersistsResult() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        String commandId = command(mockMvc, projectId, uniqueSlug("drift-hard"), "DRIFT_CHECK",
                driftPayload(fixture.get("serviceId").asText(), fixture.get("environmentId").asText()).toString()).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-drift-hard");
        tick(mockMvc, projectId, "runner-drift-hard", "DRIFT_CHECK");
        assertThat(listDrift(mockMvc, projectId, "OPEN").get(0).get("status").asText()).isEqualTo("OPEN");
        assertThat(latestResult(commandId)).contains("findings");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RolloutStartCommandExecutionHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void rolloutStartCommandStartsRealRolloutAndIdempotencyPreventsDuplicateRollout() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String commandId = command(mockMvc, ready.projectId(), uniqueSlug("rollout-start"), "ROLLOUT_START",
                "{\"planId\":\"%s\",\"startedBy\":\"release-manager@example.com\",\"reason\":\"async start\"}".formatted(ready.planId()))
                .get("commandId").asText();
        registerRunner(mockMvc, ready.projectId(), "runner-rollout-start");
        tick(mockMvc, ready.projectId(), "runner-rollout-start", "ROLLOUT_START");
        assertThat(jdbcTemplate.queryForObject("select count(*) from rollout_executions where deployment_plan_id = ?::uuid",
                Integer.class, ready.planId())).isEqualTo(1);
        assertThat(latestResult(commandId)).contains("RUNNING");
        command(mockMvc, ready.projectId(), uniqueSlug("rollout-start-dup"), "ROLLOUT_START",
                "{\"planId\":\"%s\",\"startedBy\":\"release-manager@example.com\",\"reason\":\"async start\"}".formatted(ready.planId()));
        registerRunner(mockMvc, ready.projectId(), "runner-rollout-start-2");
        tick(mockMvc, ready.projectId(), "runner-rollout-start-2", "ROLLOUT_START");
        assertThat(jdbcTemplate.queryForObject("select count(*) from rollout_executions where deployment_plan_id = ?::uuid",
                Integer.class, ready.planId())).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RolloutAdvanceCommandExecutionIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void rolloutAdvanceCommandRequiresPassedGatesAndAdvancesWave() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/rollouts/{rolloutId}/advance", ready.projectId(), rolloutId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"release-manager@example.com\",\"reason\":\"blocked before gates\"}"))
                .andExpect(status().isConflict());
        command(mockMvc, ready.projectId(), uniqueSlug("advance-block"), "ROLLOUT_ADVANCE",
                "{\"rolloutId\":\"%s\",\"advancedBy\":\"release-manager@example.com\",\"reason\":\"advance\"}".formatted(rolloutId));
        registerRunner(mockMvc, ready.projectId(), "runner-advance");
        evaluateWaveGates(mockMvc, ready.projectId(), rolloutId, 1, ready.syntheticGateId(), ready.metricGateId(), 0.2);
        tick(mockMvc, ready.projectId(), "runner-advance", "ROLLOUT_ADVANCE");
        assertThat(jdbcTemplate.queryForObject("select current_wave_number from rollout_executions where id = ?::uuid",
                Integer.class, rolloutId)).isEqualTo(2);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RollbackStartCommandExecutionHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void rollbackStartCommandCreatesRealRollbackExecution() throws Exception {
        JsonNode fixture = readyRollbackFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        command(mockMvc, projectId, uniqueSlug("rollback-start"), "ROLLBACK_START",
                "{\"rollbackRecommendationId\":\"%s\",\"startedBy\":\"release-manager@example.com\",\"reason\":\"async rollback\"}"
                        .formatted(fixture.get("recommendationId").asText()));
        registerRunner(mockMvc, projectId, "runner-rollback-start");
        JsonNode result = tick(mockMvc, projectId, "runner-rollback-start", "ROLLBACK_START");
        assertThat(result.get("command").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from rollback_executions where rollback_recommendation_id = ?::uuid",
                Integer.class, fixture.get("recommendationId").asText())).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RollbackRetryCommandExecutionIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void rollbackRetryCommandMovesFailedRollbackToRunningAndIncrementsRetryCount() throws Exception {
        JsonNode fixture = readyRollbackFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        String rollbackId = startRollback(mockMvc, projectId, fixture.get("recommendationId").asText()).get("id").asText();
        completeRollbackFailure(mockMvc, projectId, rollbackId);
        command(mockMvc, projectId, uniqueSlug("rollback-retry"), "ROLLBACK_RETRY",
                "{\"rollbackExecutionId\":\"%s\",\"requestedBy\":\"release-manager@example.com\",\"reason\":\"retry\"}".formatted(rollbackId));
        registerRunner(mockMvc, projectId, "runner-rollback-retry");
        tick(mockMvc, projectId, "runner-rollback-retry", "ROLLBACK_RETRY");
        assertThat(jdbcTemplate.queryForObject("select status from rollback_executions where id = ?::uuid", String.class, rollbackId))
                .isEqualTo("RUNNING");
        assertThat(jdbcTemplate.queryForObject("select retry_count from rollback_executions where id = ?::uuid", Integer.class, rollbackId))
                .isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CreateRepairIntentCommandExecutionIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void createRepairIntentCommandCreatesSingleIntentForFinding() throws Exception {
        JsonNode drift = createBasicDrift(mockMvc);
        String projectId = drift.get("projectId").asText();
        String findingId = listDrift(mockMvc, projectId, "OPEN").get(0).get("driftFindingId").asText();
        command(mockMvc, projectId, uniqueSlug("repair-intent"), "CREATE_REPAIR_INTENT",
                "{\"driftFindingId\":\"%s\",\"intentType\":\"REDEPLOY_DESIRED_ARTIFACT\",\"requestedBy\":\"operator@example.com\",\"reason\":\"fix drift\"}"
                        .formatted(findingId));
        registerRunner(mockMvc, projectId, "runner-repair-intent");
        tick(mockMvc, projectId, "runner-repair-intent", "CREATE_REPAIR_INTENT");
        assertThat(jdbcTemplate.queryForObject("select count(*) from drift_repair_intents where drift_finding_id = ?::uuid",
                Integer.class, findingId)).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconcileEnvironmentCommandExecutionIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void reconcileCommandCreatesRunIssuesAndRepairPlans() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        command(mockMvc, projectId, uniqueSlug("reconcile-command"), "RECONCILE_ENVIRONMENT",
                "{\"idempotencyKey\":\"%s\",\"serviceId\":\"%s\",\"environmentId\":\"%s\",\"requestedBy\":\"operator@example.com\",\"reason\":\"async reconcile\"}"
                        .formatted(uniqueSlug("reconcile-key"), fixture.get("serviceId").asText(), fixture.get("environmentId").asText()));
        registerRunner(mockMvc, projectId, "runner-reconcile");
        JsonNode result = tick(mockMvc, projectId, "runner-reconcile", "RECONCILE_ENVIRONMENT");
        assertThat(result.get("command").get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from reconciliation_issues where project_id = ?::uuid",
                Integer.class, projectId)).isGreaterThanOrEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from repair_plans where project_id = ?::uuid",
                Integer.class, projectId)).isGreaterThanOrEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class CommandExecutionResultPersistenceIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void successfulTickPersistsAttemptResultJson() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("result"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-result");
        tick(mockMvc, projectId, "runner-result", "VERIFY_CONSISTENCY");
        assertThat(latestResult(commandId)).contains("\"projectId\"");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationPolicyPrecedenceIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void serviceEnvironmentPolicyOverridesGlobalPolicy() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        putPolicy(mockMvc, projectId);
        json(mockMvc.perform(put("/api/v1/projects/{projectId}/reconciliation-policies", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":"%s","environmentId":"%s","enabled":true,"requireApprovalForRepair":false,"createdBy":"operator@example.com","reason":"specific policy"}
                                """.formatted(fixture.get("serviceId").asText(), fixture.get("environmentId").asText())))
                .andExpect(status().isOk()).andReturn());
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        reconcile(mockMvc, projectId, uniqueSlug("policy-precedence"), fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        assertThat(jdbcTemplate.queryForObject("select bool_or(not requires_approval) from repair_plans where project_id = ?::uuid",
                Boolean.class, projectId)).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationRunIdempotencyHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void reconciliationReplayDoesNotDuplicateIssuesOrPlansAndChangedRequestConflicts() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        String key = uniqueSlug("recon-idem");
        reconcile(mockMvc, projectId, key, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        int issues = jdbcTemplate.queryForObject("select count(*) from reconciliation_issues where project_id = ?::uuid", Integer.class, projectId);
        reconcile(mockMvc, projectId, key, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        assertThat(jdbcTemplate.queryForObject("select count(*) from reconciliation_issues where project_id = ?::uuid", Integer.class, projectId))
                .isEqualTo(issues);
        mockMvc.perform(post("/api/v1/projects/{projectId}/reconciliation-runs", projectId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedBy\":\"operator@example.com\",\"reason\":\"changed\"}"))
                .andExpect(status().isConflict());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationIssueClassificationIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void openAndAcknowledgedDriftBecomeIssuesButResolvedDriftIsExcluded() throws Exception {
        JsonNode drift = createBasicDrift(mockMvc);
        String projectId = drift.get("projectId").asText();
        String findingId = listDrift(mockMvc, projectId, "OPEN").get(0).get("driftFindingId").asText();
        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/acknowledge", projectId, findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acknowledgedBy\":\"operator@example.com\",\"reason\":\"known\"}"))
                .andExpect(status().isOk()).andReturn());
        JsonNode fixture = createDriftFixture(mockMvc);
        String otherProjectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, otherProjectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        String resolvedId = listDrift(mockMvc, otherProjectId, "OPEN").get(0).get("driftFindingId").asText();
        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/resolve", otherProjectId, resolvedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolvedBy\":\"operator@example.com\",\"reason\":\"closed\"}"))
                .andExpect(status().isOk()).andReturn());
        reconcile(mockMvc, projectId, uniqueSlug("issue-class"), null, null);
        assertThat(jdbcTemplate.queryForObject("select issue_type from reconciliation_issues where project_id = ?::uuid limit 1",
                String.class, projectId)).isIn("ARTIFACT_DRIFT", "MANUAL_CHANGE");
        assertThat(jdbcTemplate.queryForObject("select count(*) from reconciliation_issues where project_id = ?::uuid",
                Integer.class, otherProjectId)).isZero();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RepairPlanEvidenceSnapshotIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void repairPlanCapturesDriftEvidenceSnapshot() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        reconcile(mockMvc, projectId, uniqueSlug("evidence-snapshot"), fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        String snapshot = jdbcTemplate.queryForObject("select evidence_snapshot_json::text from repair_plans where project_id = ?::uuid limit 1",
                String.class, projectId);
        assertThat(snapshot).contains("finding").contains("desired").contains("actual");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RepairPlanApprovalGateIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void executionRecommendationRequiresApprovalWhenPolicyRequiresIt() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        putPolicy(mockMvc, projectId);
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        reconcile(mockMvc, projectId, uniqueSlug("approval-gate"), fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        String planId = jdbcTemplate.queryForObject("select id::text from repair_plans where project_id = ?::uuid limit 1", String.class, projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/repair-plans/{repairPlanId}/recommend-execution", projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedBy\":\"operator@example.com\",\"reason\":\"try\"}"))
                .andExpect(status().isConflict());
        assertThat(approvePlan(mockMvc, projectId, planId).get("status").asText()).isEqualTo("APPROVED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class RepairPlanCommandGenerationHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void approvedRepairPlanRecommendationCreatesCommandButDoesNotRunIt() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        reconcile(mockMvc, projectId, uniqueSlug("command-gen"), fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        String planId = jdbcTemplate.queryForObject("select id::text from repair_plans where project_id = ?::uuid limit 1", String.class, projectId);
        approvePlan(mockMvc, projectId, planId);
        JsonNode response = json(mockMvc.perform(post("/api/v1/projects/{projectId}/repair-plans/{repairPlanId}/recommend-execution",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedBy\":\"operator@example.com\",\"reason\":\"recommend\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(response.get("command").get("commandType").asText()).isEqualTo("CREATE_REPAIR_INTENT");
        assertThat(response.get("command").get("status").asText()).isEqualTo("PENDING");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class ReconciliationCommandFunctionalIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void reconciliationCommandResultIncludesRunEvidence() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        String commandId = command(mockMvc, projectId, uniqueSlug("reconcile-functional"), "RECONCILE_ENVIRONMENT",
                "{\"idempotencyKey\":\"%s\",\"serviceId\":\"%s\",\"environmentId\":\"%s\",\"requestedBy\":\"operator@example.com\",\"reason\":\"async reconcile\"}"
                        .formatted(uniqueSlug("reconcile-functional-key"), fixture.get("serviceId").asText(), fixture.get("environmentId").asText()))
                .get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-reconcile-functional");
        tick(mockMvc, projectId, "runner-reconcile-functional", "RECONCILE_ENVIRONMENT");
        assertThat(latestResult(commandId)).contains("repairPlans").contains("issues");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StuckCommandDetectorHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void detectorReturnsOnlyExpiredActiveCommands() throws Exception {
        String projectId = createProject(mockMvc);
        String staleId = command(mockMvc, projectId, uniqueSlug("stuck"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        String freshId = command(mockMvc, projectId, uniqueSlug("fresh-running"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        String doneId = command(mockMvc, projectId, uniqueSlug("done"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-stuck");
        claim(mockMvc, projectId, "runner-stuck", 60, "VERIFY_CONSISTENCY");
        claim(mockMvc, projectId, "runner-stuck", 60, "VERIFY_CONSISTENCY");
        jdbcTemplate.update("update deployment_commands set status='SUCCEEDED', leased_by_node_id=null, lease_expires_at=null where id = ?::uuid", doneId);
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", staleId);
        JsonNode stuck = getJson(mockMvc, "/api/v1/projects/{projectId}/ops/stuck-commands", projectId);
        assertThat(stuck.toString()).contains(staleId);
        assertThat(stuck.toString()).doesNotContain(freshId).doesNotContain(doneId);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StaleLeaseReleaseHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void staleLeaseReleaseRejectsFreshLeaseThenClearsExpiredLease() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("release-stale"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-release-stale");
        claim(mockMvc, projectId, "runner-release-stale", 60, "VERIFY_CONSISTENCY");
        mockMvc.perform(post("/api/v1/projects/{projectId}/ops/leases/{commandId}/force-release-stale", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recoveryRequest().toString()))
                .andExpect(status().isConflict());
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        JsonNode released = force(mockMvc, "/api/v1/projects/{projectId}/ops/leases/{commandId}/force-release-stale", projectId, commandId);
        assertThat(released.get("status").asText()).isEqualTo("PENDING");
        assertThat(released.get("leasedByNodeId").isNull()).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperatorForceParkHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void forceParkRequiresRiskAckParksAndBlocksClaim() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("force-park-hard"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/ops/commands/{commandId}/force-park", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"operator@example.com\",\"reason\":\"missing risk\"}"))
                .andExpect(status().isBadRequest());
        assertThat(force(mockMvc, "/api/v1/projects/{projectId}/ops/commands/{commandId}/force-park", projectId, commandId)
                .get("status").asText()).isEqualTo("PARKED");
        registerRunner(mockMvc, projectId, "runner-force-park");
        assertThat(claim(mockMvc, projectId, "runner-force-park", 60, "VERIFY_CONSISTENCY").get("status").asText()).isEqualTo("NO_COMMAND");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperatorForceRetryHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void forceRetryClearsLeaseAndAllowsExecution() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("force-retry-hard"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-force-retry");
        claim(mockMvc, projectId, "runner-force-retry", 60, "VERIFY_CONSISTENCY");
        JsonNode retried = force(mockMvc, "/api/v1/projects/{projectId}/ops/commands/{commandId}/force-retry", projectId, commandId);
        assertThat(retried.get("status").asText()).isEqualTo("PENDING");
        JsonNode claimed = claim(mockMvc, projectId, "runner-force-retry", 60, "VERIFY_CONSISTENCY");
        assertThat(succeed(mockMvc, projectId, "runner-force-retry", commandId, claimed.get("fencingToken").asLong()).get("status").asText())
                .isEqualTo("SUCCEEDED");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperatorManualResolutionHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void manualResolutionRequiresRiskAckAndTerminalCommandCannotBeClaimedAgain() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("manual-hard"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        mockMvc.perform(post("/api/v1/projects/{projectId}/ops/commands/{commandId}/mark-manually-resolved", projectId, commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"operator@example.com\",\"reason\":\"missing risk\"}"))
                .andExpect(status().isBadRequest());
        assertThat(force(mockMvc, "/api/v1/projects/{projectId}/ops/commands/{commandId}/mark-manually-resolved", projectId, commandId)
                .get("status").asText()).isEqualTo("SUCCEEDED");
        registerRunner(mockMvc, projectId, "runner-manual-hard");
        assertThat(claim(mockMvc, projectId, "runner-manual-hard", 60, "VERIFY_CONSISTENCY").get("status").asText()).isEqualTo("NO_COMMAND");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StuckRolloutDetectorHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void stuckRolloutDetectorReturnsActiveRolloutButNotTerminalRollout() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String rolloutId = startRollout(mockMvc, ready).get("id").asText();
        JsonNode stuck = getJson(mockMvc, "/api/v1/projects/{projectId}/ops/stuck-rollouts", ready.projectId());
        assertThat(stuck.toString()).contains(rolloutId);
        jdbcTemplate.update("update rollout_executions set status = 'SUCCEEDED' where id = ?::uuid", rolloutId);
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/stuck-rollouts", ready.projectId()).toString())
                .doesNotContain(rolloutId);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class StuckRollbackDetectorHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void stuckRollbackDetectorReturnsRunningRollbackButNotTerminalRollback() throws Exception {
        JsonNode fixture = readyRollbackFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        String rollbackId = startRollback(mockMvc, projectId, fixture.get("recommendationId").asText()).get("id").asText();
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/stuck-rollbacks", projectId).toString()).contains(rollbackId);
        jdbcTemplate.update("update rollback_executions set status = 'SUCCEEDED' where id = ?::uuid", rollbackId);
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/stuck-rollbacks", projectId).toString()).doesNotContain(rollbackId);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperationalSummaryHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void summaryCountsCommandsDriftRepairPlansAndActiveWork() throws Exception {
        JsonNode fixture = createDriftFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        reconcile(mockMvc, projectId, uniqueSlug("summary-recon"), fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        command(mockMvc, projectId, uniqueSlug("summary-command"), "VERIFY_CONSISTENCY", "{}");
        JsonNode summary = getJson(mockMvc, "/api/v1/projects/{projectId}/ops/summary", projectId);
        assertThat(summary.get("openDriftCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(summary.get("proposedRepairPlanCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(summary.get("commands").isArray()).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class DeploymentInvestigationSearchHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void investigationFiltersByStatusAndProjectScope() throws Exception {
        String projectId = createProject(mockMvc);
        String otherProjectId = createProject(mockMvc);
        command(mockMvc, projectId, uniqueSlug("investigation-pending"), "VERIFY_CONSISTENCY", "{}");
        command(mockMvc, otherProjectId, uniqueSlug("investigation-other"), "VERIFY_CONSISTENCY", "{}");
        JsonNode investigation = json(mockMvc.perform(get("/api/v1/projects/{projectId}/ops/investigate", projectId)
                        .param("commandStatus", "PENDING"))
                .andExpect(status().isOk()).andReturn());
        assertThat(investigation.get("commands").size()).isEqualTo(1);
        assertThat(investigation.toString()).contains(projectId).doesNotContain(otherProjectId);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class OperatorRecoveryEventTrailIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void operatorRecoveryActionAndIntentEventAreRecorded() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("event-trail"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        force(mockMvc, "/api/v1/projects/{projectId}/ops/commands/{commandId}/force-park", projectId, commandId);
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/ops/recovery-actions", projectId).get(0).get("actionType").asText())
                .isEqualTo("FORCE_PARK_COMMAND");
        assertThat(eventCount(projectId, "OPERATOR_RECOVERY_ACTION_RECORDED", commandId)).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class FullDeploymentLifecycleProofIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void asyncRolloutCompletesCanaryAndUpdatesEnvironmentAndDesiredState() throws Exception {
        ReadyPlan ready = readyPlan(mockMvc);
        String commandId = command(mockMvc, ready.projectId(), uniqueSlug("full-deploy"), "ROLLOUT_START",
                "{\"planId\":\"%s\",\"startedBy\":\"release-manager@example.com\",\"reason\":\"full deploy\"}".formatted(ready.planId()))
                .get("commandId").asText();
        registerRunner(mockMvc, ready.projectId(), "runner-full-deploy");
        String rolloutId = tick(mockMvc, ready.projectId(), "runner-full-deploy", "ROLLOUT_START")
                .get("result").get("id").asText();
        for (int wave = 1; wave <= 4; wave++) {
            evaluateWaveGates(mockMvc, ready.projectId(), rolloutId, wave, ready.syntheticGateId(), ready.metricGateId(), 0.2);
            advanceRollout(mockMvc, ready.projectId(), rolloutId);
        }
        assertThat(jdbcTemplate.queryForObject("select status from rollout_executions where id = ?::uuid", String.class, rolloutId))
                .isEqualTo("SUCCEEDED");
        assertThat(desiredState(mockMvc, ready.projectId(), ready.serviceId(), ready.prodId()).get("desiredArtifactId").asText())
                .isEqualTo(ready.artifactId());
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/deployment-consistency", ready.projectId()).get("consistent").asBoolean())
                .isTrue();
        assertThat(eventCount(ready.projectId(), "COMMAND_SUCCEEDED", commandId)).isEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class FullFailureLifecycleProofIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void failedCanaryKeepsStableArtifactAndProducesRollbackRecommendation() throws Exception {
        JsonNode fixture = readyRollbackFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        assertThat(jdbcTemplate.queryForObject("select status from rollout_executions where id = ?::uuid",
                String.class, fixture.get("rolloutId").asText())).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("select current_artifact_id::text from environment_deployment_states where service_id = ?::uuid and environment_id = ?::uuid",
                String.class, fixture.get("serviceId").asText(), fixture.get("environmentId").asText()))
                .isEqualTo(fixture.get("stableArtifactId").asText());
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/rollouts/{rolloutId}/recovery", projectId,
                fixture.get("rolloutId").asText()).get("recommendedAction").asText()).isEqualTo("START_ROLLBACK");
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class FullRollbackDriftRepairProofIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void rollbackDriftReconciliationApprovalAndRepairIntentChainIsProven() throws Exception {
        JsonNode fixture = readyRollbackFixture(mockMvc);
        String projectId = fixture.get("projectId").asText();
        command(mockMvc, projectId, uniqueSlug("full-rollback"), "ROLLBACK_START",
                "{\"rollbackRecommendationId\":\"%s\",\"startedBy\":\"release-manager@example.com\",\"reason\":\"rollback\"}"
                        .formatted(fixture.get("recommendationId").asText()));
        registerRunner(mockMvc, projectId, "runner-full-rollback");
        String rollbackId = tick(mockMvc, projectId, "runner-full-rollback", "ROLLBACK_START")
                .get("result").get("id").asText();
        evaluateRollbackGates(mockMvc, projectId, rollbackId, fixture.get("syntheticGateId").asText(), fixture.get("metricGateId").asText(), 0.2);
        completeRollbackSuccess(mockMvc, projectId, rollbackId);
        String targetId = registerTarget(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText())
                .get("runtimeTargetId").asText();
        heartbeat(mockMvc, projectId, targetId, OffsetDateTime.now(ZoneOffset.UTC));
        deploymentReport(mockMvc, projectId, targetId, fixture.get("failedArtifactId").asText(), "sha256:failed-runtime", "RUNNING");
        checkDrift(mockMvc, projectId, fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        reconcile(mockMvc, projectId, uniqueSlug("full-repair"), fixture.get("serviceId").asText(), fixture.get("environmentId").asText());
        String planId = jdbcTemplate.queryForObject("select id::text from repair_plans where project_id = ?::uuid limit 1", String.class, projectId);
        approvePlan(mockMvc, projectId, planId);
        JsonNode recommended = json(mockMvc.perform(post("/api/v1/projects/{projectId}/repair-plans/{repairPlanId}/recommend-execution",
                        projectId, planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestedBy\":\"operator@example.com\",\"reason\":\"repair\"}"))
                .andExpect(status().isOk()).andReturn());
        String repairCommandId = recommended.get("command").get("commandId").asText();
        tick(mockMvc, projectId, "runner-full-rollback", "CREATE_REPAIR_INTENT");
        assertThat(commandStatus(repairCommandId)).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject("select count(*) from drift_repair_intents where project_id = ?::uuid",
                Integer.class, projectId)).isGreaterThanOrEqualTo(1);
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class FullAsyncRunnerFencingProofIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void staleRunnerCompletionIsRejectedAndFreshRunnerCompletesExactlyOnce() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("full-fencing"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-a");
        registerRunner(mockMvc, projectId, "runner-b");
        JsonNode first = claim(mockMvc, projectId, "runner-a", 1, "VERIFY_CONSISTENCY");
        jdbcTemplate.update("update deployment_commands set lease_expires_at = now() - interval '1 second' where id = ?::uuid", commandId);
        JsonNode second = claim(mockMvc, projectId, "runner-b", 60, "VERIFY_CONSISTENCY");
        mockMvc.perform(post("/api/v1/projects/{projectId}/runners/{nodeId}/commands/{commandId}/complete-success",
                        projectId, "runner-a", commandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fencingToken\":%d,\"result\":{}}".formatted(first.get("fencingToken").asLong())))
                .andExpect(status().isConflict());
        succeed(mockMvc, projectId, "runner-b", commandId, second.get("fencingToken").asLong());
        assertThat(attemptStatuses(commandId)).contains("STALE_REJECTED").contains("SUCCEEDED");
        assertThat(eventCount(projectId, "COMMAND_SUCCEEDED", commandId)).isEqualTo(1);
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/deployment-consistency", projectId).get("consistent").asBoolean()).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class FinalInvariantRegressionIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void finalInvariantMatrixFlagsAndProtectsCriticalStates() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("invariant-park"), "VERIFY_CONSISTENCY", "{}", 1).get("commandId").asText();
        registerRunner(mockMvc, projectId, "runner-invariant");
        JsonNode claimed = claim(mockMvc, projectId, "runner-invariant", 60, "VERIFY_CONSISTENCY");
        completeFailure(mockMvc, projectId, "runner-invariant", commandId, claimed.get("fencingToken").asLong(), "fatal");
        assertThat(claim(mockMvc, projectId, "runner-invariant", 60, "VERIFY_CONSISTENCY").get("status").asText()).isEqualTo("NO_COMMAND");

        JsonNode drift = createBasicDrift(mockMvc);
        String driftProjectId = drift.get("projectId").asText();
        String serviceId = jdbcTemplate.queryForObject("select service_id::text from drift_findings where project_id = ?::uuid limit 1",
                String.class, driftProjectId);
        String envId = jdbcTemplate.queryForObject("select environment_id::text from drift_findings where project_id = ?::uuid limit 1",
                String.class, driftProjectId);
        String desiredArtifactId = jdbcTemplate.queryForObject("select desired_artifact_id::text from desired_environment_states where project_id = ?::uuid limit 1",
                String.class, driftProjectId);
        addEvidence(mockMvc, driftProjectId, serviceId, desiredArtifactId, "TEST_REPORT");
        addEvidence(mockMvc, driftProjectId, serviceId, desiredArtifactId, "IMAGE_SCAN");
        JsonNode plan = createPlan(mockMvc, driftProjectId, serviceId,
                desiredArtifactId, envId, "CANARY", uniqueSlug("blocked-plan"));
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/deployment-plans/{planId}/readiness", driftProjectId,
                plan.get("id").asText()).get("readyToStart").asBoolean()).isFalse();
        String findingId = listDrift(mockMvc, driftProjectId, "OPEN").get(0).get("driftFindingId").asText();
        json(mockMvc.perform(post("/api/v1/projects/{projectId}/drift-findings/{driftFindingId}/resolve", driftProjectId, findingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolvedBy\":\"operator@example.com\",\"reason\":\"operator accepted risk for invariant proof\"}"))
                .andExpect(status().isOk()).andReturn());
        assertThat(getJson(mockMvc, "/api/v1/projects/{projectId}/deployment-consistency", projectId).get("consistent").asBoolean()).isTrue();
    }
}

@SpringBootTest
@AutoConfigureMockMvc
class FinalVerifierHardeningIntegrationTest extends Deploy180BProofSupport {
    @Autowired MockMvc mockMvc;

    @Test
    void verifierFlagsTerminalCommandLeaseAndUnapprovedRepairExecution() throws Exception {
        String projectId = createProject(mockMvc);
        String commandId = command(mockMvc, projectId, uniqueSlug("verifier-terminal"), "VERIFY_CONSISTENCY", "{}").get("commandId").asText();
        jdbcTemplate.update("""
                update deployment_commands
                set status = 'SUCCEEDED', leased_by_node_id = 'stale-runner', lease_expires_at = now()
                where id = ?::uuid
                """, commandId);
        jdbcTemplate.update("""
                insert into repair_plans (
                    id, project_id, plan_type, status, requires_approval, evidence_snapshot_json, requested_by, reason
                )
                values (gen_random_uuid(), ?::uuid, 'INVESTIGATE', 'EXECUTION_RECOMMENDED', true, '{}'::jsonb, 'operator@example.com', 'bad state')
                """, projectId);
        JsonNode verifier = getJson(mockMvc, "/api/v1/projects/{projectId}/deployment-consistency", projectId);
        assertThat(verifier.get("consistent").asBoolean()).isFalse();
        assertThat(verifier.toString()).contains("TERMINAL_COMMAND_STILL_LEASED", "REPAIR_PLAN_EXECUTION_WITHOUT_APPROVAL");
    }
}
