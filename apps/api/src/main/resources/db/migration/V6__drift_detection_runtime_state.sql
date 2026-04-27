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
            'DRIFT_VERIFIER_RUN','DRIFT_VERIFIER_FAILED'
        ));

create table desired_environment_states (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    desired_artifact_id uuid references release_artifacts(id),
    desired_image_digest text,
    desired_config_version text,
    desired_source text not null,
    desired_reason text not null,
    recorded_by text not null,
    rollout_execution_id uuid references rollout_executions(id),
    rollback_execution_id uuid references rollback_executions(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(service_id, environment_id),
    constraint desired_environment_states_source_check check (desired_source in ('ROLLOUT_SUCCESS','ROLLBACK_SUCCESS','OPERATOR_ACCEPTED_ACTUAL','MANUAL_BASELINE','REPAIR_INTENT')),
    constraint desired_environment_states_reason_not_blank check (length(trim(desired_reason)) > 0),
    constraint desired_environment_states_recorded_by_not_blank check (length(trim(recorded_by)) > 0)
);
create index idx_desired_environment_states_project_id on desired_environment_states(project_id);
create index idx_desired_environment_states_service_environment on desired_environment_states(service_id, environment_id);
create index idx_desired_environment_states_artifact on desired_environment_states(desired_artifact_id);

create table runtime_targets (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    target_key text not null,
    target_type text not null,
    display_name text not null,
    status text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    registered_by text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(service_id, environment_id, target_key),
    constraint runtime_targets_type_check check (target_type in ('SIMULATED_RUNTIME','VM_GROUP','CONTAINER_GROUP','EXTERNAL_SYSTEM','OTHER')),
    constraint runtime_targets_status_check check (status in ('ACTIVE','INACTIVE','STALE')),
    constraint runtime_targets_key_not_blank check (length(trim(target_key)) > 0),
    constraint runtime_targets_display_name_not_blank check (length(trim(display_name)) > 0),
    constraint runtime_targets_registered_by_not_blank check (length(trim(registered_by)) > 0)
);
create index idx_runtime_targets_project_id on runtime_targets(project_id);
create index idx_runtime_targets_service_environment on runtime_targets(service_id, environment_id);
create index idx_runtime_targets_status on runtime_targets(status);

create table runtime_target_heartbeats (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    runtime_target_id uuid not null references runtime_targets(id),
    status text not null,
    reported_by text not null,
    heartbeat_at timestamptz not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint runtime_target_heartbeats_status_check check (status in ('HEALTHY','DEGRADED','UNREACHABLE')),
    constraint runtime_target_heartbeats_reported_by_not_blank check (length(trim(reported_by)) > 0)
);
create index idx_runtime_target_heartbeats_project_id on runtime_target_heartbeats(project_id);
create index idx_runtime_target_heartbeats_target_at on runtime_target_heartbeats(runtime_target_id, heartbeat_at);
create index idx_runtime_target_heartbeats_status on runtime_target_heartbeats(status);

create table runtime_deployment_reports (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    runtime_target_id uuid not null references runtime_targets(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    reported_artifact_id uuid references release_artifacts(id),
    reported_image_digest text,
    reported_version text,
    report_status text not null,
    reported_by text not null,
    observed_at timestamptz not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint runtime_deployment_reports_status_check check (report_status in ('RUNNING','MISSING','UNKNOWN','ERROR')),
    constraint runtime_deployment_reports_reported_by_not_blank check (length(trim(reported_by)) > 0)
);
create index idx_runtime_deployment_reports_project_id on runtime_deployment_reports(project_id);
create index idx_runtime_deployment_reports_target_at on runtime_deployment_reports(runtime_target_id, observed_at);
create index idx_runtime_deployment_reports_service_env_at on runtime_deployment_reports(service_id, environment_id, observed_at);
create index idx_runtime_deployment_reports_status on runtime_deployment_reports(report_status);

create table runtime_config_reports (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    runtime_target_id uuid not null references runtime_targets(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    config_version text,
    config_digest text,
    report_status text not null,
    reported_by text not null,
    observed_at timestamptz not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint runtime_config_reports_status_check check (report_status in ('PRESENT','MISSING','UNKNOWN','ERROR')),
    constraint runtime_config_reports_reported_by_not_blank check (length(trim(reported_by)) > 0)
);
create index idx_runtime_config_reports_project_id on runtime_config_reports(project_id);
create index idx_runtime_config_reports_target_at on runtime_config_reports(runtime_target_id, observed_at);
create index idx_runtime_config_reports_service_env_at on runtime_config_reports(service_id, environment_id, observed_at);
create index idx_runtime_config_reports_status on runtime_config_reports(report_status);

create table drift_findings (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    runtime_target_id uuid references runtime_targets(id),
    desired_state_id uuid references desired_environment_states(id),
    drift_type text not null,
    severity text not null,
    status text not null,
    desired_json jsonb not null default '{}'::jsonb,
    actual_json jsonb not null default '{}'::jsonb,
    message text not null,
    recommended_action text not null,
    first_detected_at timestamptz not null default now(),
    last_detected_at timestamptz not null default now(),
    acknowledged_at timestamptz,
    acknowledged_by text,
    acknowledgement_reason text,
    resolved_at timestamptz,
    resolved_by text,
    resolution_reason text,
    metadata_json jsonb not null default '{}'::jsonb,
    constraint drift_findings_type_check check (drift_type in ('ARTIFACT_DRIFT','CONFIG_DRIFT','MISSING_DEPLOYMENT','MANUAL_CHANGE','STALE_TARGET_REPORT','UNKNOWN_ACTUAL_STATE','ROLLBACK_MISMATCH')),
    constraint drift_findings_severity_check check (severity in ('INFO','WARNING','CRITICAL')),
    constraint drift_findings_status_check check (status in ('OPEN','ACKNOWLEDGED','RESOLVED')),
    constraint drift_findings_action_check check (recommended_action in ('REDEPLOY_DESIRED_ARTIFACT','ROLLBACK_TO_STABLE','ACCEPT_ACTUAL_AS_DESIRED','ACKNOWLEDGE_MANUAL_CHANGE','INVESTIGATE','MANUAL_INTERVENTION_REQUIRED','NONE')),
    constraint drift_findings_message_not_blank check (length(trim(message)) > 0)
);
create unique index uq_drift_open_finding on drift_findings(project_id, service_id, environment_id, coalesce(runtime_target_id, '00000000-0000-0000-0000-000000000000'::uuid), drift_type)
    where status in ('OPEN','ACKNOWLEDGED');
create index idx_drift_findings_project_id on drift_findings(project_id);
create index idx_drift_findings_service_environment on drift_findings(service_id, environment_id);
create index idx_drift_findings_runtime_target on drift_findings(runtime_target_id);
create index idx_drift_findings_status on drift_findings(status);
create index idx_drift_findings_type on drift_findings(drift_type);
create index idx_drift_findings_severity on drift_findings(severity);
create index idx_drift_findings_first_detected on drift_findings(first_detected_at);

create table drift_repair_intents (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    drift_finding_id uuid not null references drift_findings(id),
    intent_type text not null,
    status text not null,
    requested_by text not null,
    reason text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    completed_at timestamptz,
    completed_by text,
    completion_reason text,
    constraint drift_repair_intents_type_check check (intent_type in ('REDEPLOY_DESIRED_ARTIFACT','ROLLBACK_TO_STABLE','ACCEPT_ACTUAL_AS_DESIRED','ACKNOWLEDGE_MANUAL_CHANGE','INVESTIGATE','MANUAL_INTERVENTION_REQUIRED')),
    constraint drift_repair_intents_status_check check (status in ('OPEN','COMPLETED','CANCELLED')),
    constraint drift_repair_intents_requested_by_not_blank check (length(trim(requested_by)) > 0),
    constraint drift_repair_intents_reason_not_blank check (length(trim(reason)) > 0)
);
create unique index uq_drift_open_repair_intent on drift_repair_intents(drift_finding_id, intent_type) where status = 'OPEN';
create index idx_drift_repair_intents_project_id on drift_repair_intents(project_id);
create index idx_drift_repair_intents_finding on drift_repair_intents(drift_finding_id);
