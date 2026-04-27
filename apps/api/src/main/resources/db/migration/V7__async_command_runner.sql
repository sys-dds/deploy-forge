alter table deployment_intent_events
    drop constraint deployment_intent_events_type_check,
    add constraint deployment_intent_events_type_check
        check (event_type in (
            'ARTIFACT_REGISTERED','ARTIFACT_EVIDENCE_ADDED','PLAN_CREATED','PLAN_IDEMPOTENT_REPLAYED',
            'PLAN_IDEMPOTENCY_CONFLICT','PLAN_CANCELLED','PLAN_ABORTED','ENVIRONMENT_STATE_PLANNED',
            'DEPLOYABILITY_CHECK_FAILED','PROMOTION_RULE_UPSERTED','PROMOTION_EVIDENCE_RECORDED',
            'PROTECTED_POLICY_UPSERTED','APPROVAL_REQUESTED','APPROVAL_APPROVED','APPROVAL_REJECTED',
            'APPROVAL_EXPIRED','DEPLOYMENT_LOCK_ACQUIRED','DEPLOYMENT_LOCK_RELEASED','DEPLOYMENT_LOCK_EXPIRED',
            'DEPLOYMENT_LOCK_STALE_RELEASED','OVERRIDE_RECORDED','GATE_DEFINITION_CREATED','GATE_EVALUATED',
            'GATE_OVERRIDDEN','GATE_RERUN_REQUESTED','ROLLOUT_STARTED','ROLLOUT_WAVE_STARTED',
            'ROLLOUT_WAVE_COMPLETED','ROLLOUT_WAVE_FAILED','ROLLOUT_PAUSED','ROLLOUT_RESUMED',
            'ROLLOUT_ABORTED','ROLLOUT_SUCCEEDED','ROLLOUT_FAILED','ROLLBACK_RECOMMENDED','ROLLBACK_STARTED',
            'ROLLBACK_GATE_EVALUATED','ROLLBACK_SUCCEEDED','ROLLBACK_FAILED','ROLLBACK_RETRY_REQUESTED',
            'ROLLBACK_MANUAL_INTERVENTION_REQUIRED','ROLLBACK_ACKNOWLEDGED','RECOVERY_ACTION_RECORDED',
            'ENVIRONMENT_STATE_UPDATED','CONSISTENCY_VERIFIER_RUN','CONSISTENCY_VERIFIER_FAILED',
            'DESIRED_STATE_RECORDED','RUNTIME_TARGET_REGISTERED','RUNTIME_TARGET_HEARTBEAT_RECORDED',
            'RUNTIME_REPORT_INGESTED','ACTUAL_STATE_REPORTED','CONFIG_STATE_REPORTED','DRIFT_DETECTED',
            'DRIFT_ACKNOWLEDGED','DRIFT_RESOLVED','DRIFT_REPAIR_RECOMMENDED','DRIFT_REPAIR_INTENT_CREATED',
            'ACTUAL_ACCEPTED_AS_DESIRED','MANUAL_CHANGE_ACKNOWLEDGED','REDEPLOY_DESIRED_RECOMMENDED',
            'DRIFT_VERIFIER_RUN','DRIFT_VERIFIER_FAILED',
            'COMMAND_CREATED','COMMAND_LEASED','COMMAND_STARTED','COMMAND_SUCCEEDED','COMMAND_FAILED',
            'COMMAND_PARKED','COMMAND_REQUEUED','COMMAND_STALE_COMPLETION_REJECTED',
            'RUNNER_REGISTERED','RUNNER_HEARTBEAT_RECORDED','RUNNER_MARKED_STALE',
            'RECONCILIATION_RUN_CREATED','REPAIR_PLAN_CREATED','REPAIR_PLAN_APPROVED',
            'REPAIR_PLAN_EXECUTION_RECOMMENDED','OPERATOR_RECOVERY_ACTION_RECORDED','FINAL_INVARIANT_CHECK_RUN'
        ));

create table runner_nodes (
    id uuid primary key,
    node_id text not null unique,
    status text not null,
    started_at timestamptz not null default now(),
    last_seen_at timestamptz not null default now(),
    metadata_json jsonb not null default '{}'::jsonb,
    constraint runner_nodes_status_check check (status in ('ACTIVE','STALE','STOPPED')),
    constraint runner_nodes_node_id_not_blank check (length(trim(node_id)) > 0)
);

create table deployment_commands (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    command_type text not null,
    status text not null,
    idempotency_key text not null,
    request_hash text not null,
    payload_json jsonb not null default '{}'::jsonb,
    priority int not null default 100,
    attempts int not null default 0,
    max_attempts int not null default 3,
    next_attempt_at timestamptz not null default now(),
    leased_by_node_id text,
    lease_expires_at timestamptz,
    fencing_token bigint not null default 1,
    last_error text,
    parked_reason text,
    created_by text not null,
    reason text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    unique(project_id, idempotency_key),
    constraint deployment_commands_type_check check (command_type in (
        'ROLLOUT_START','ROLLOUT_ADVANCE','ROLLOUT_PAUSE','ROLLOUT_RESUME','ROLLOUT_ABORT',
        'ROLLBACK_START','ROLLBACK_COMPLETE_SUCCESS','ROLLBACK_COMPLETE_FAILURE','ROLLBACK_RETRY',
        'DRIFT_CHECK','RECONCILE_ENVIRONMENT','CREATE_REPAIR_INTENT','VERIFY_CONSISTENCY'
    )),
    constraint deployment_commands_status_check check (status in ('PENDING','LEASED','RUNNING','SUCCEEDED','FAILED','PARKED','CANCELLED')),
    constraint deployment_commands_idempotency_key_not_blank check (length(trim(idempotency_key)) > 0),
    constraint deployment_commands_created_by_not_blank check (length(trim(created_by)) > 0),
    constraint deployment_commands_reason_not_blank check (length(trim(reason)) > 0),
    constraint deployment_commands_attempts_check check (attempts >= 0 and max_attempts > 0)
);

create table deployment_command_attempts (
    id uuid primary key,
    command_id uuid not null references deployment_commands(id),
    attempt_number int not null,
    runner_node_id text not null,
    fencing_token bigint not null,
    status text not null,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    error_message text,
    result_json jsonb not null default '{}'::jsonb,
    unique(command_id, attempt_number),
    constraint deployment_command_attempts_status_check check (status in ('RUNNING','SUCCEEDED','FAILED','STALE_REJECTED')),
    constraint deployment_command_attempts_attempt_check check (attempt_number > 0)
);

create table parked_deployment_commands (
    id uuid primary key,
    command_id uuid not null references deployment_commands(id),
    parked_by text not null,
    reason text not null,
    parked_at timestamptz not null default now(),
    requeued_at timestamptz,
    requeued_by text,
    requeue_reason text,
    constraint parked_deployment_commands_parked_by_not_blank check (length(trim(parked_by)) > 0),
    constraint parked_deployment_commands_reason_not_blank check (length(trim(reason)) > 0)
);

create index idx_deployment_commands_project_status on deployment_commands(project_id, status);
create index idx_deployment_commands_status_next_attempt on deployment_commands(status, next_attempt_at);
create index idx_deployment_commands_type_status on deployment_commands(command_type, status);
create index idx_deployment_commands_leased_by on deployment_commands(leased_by_node_id);
create index idx_deployment_command_attempts_command_id on deployment_command_attempts(command_id);
create index idx_runner_nodes_status_last_seen on runner_nodes(status, last_seen_at);
create index idx_parked_deployment_commands_command_id on parked_deployment_commands(command_id);
