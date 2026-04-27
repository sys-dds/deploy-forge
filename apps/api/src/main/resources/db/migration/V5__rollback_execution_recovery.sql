alter table deployment_intent_events
    drop constraint deployment_intent_events_type_check,
    add constraint deployment_intent_events_type_check
        check (event_type in (
            'ARTIFACT_REGISTERED',
            'ARTIFACT_EVIDENCE_ADDED',
            'PLAN_CREATED',
            'PLAN_IDEMPOTENT_REPLAYED',
            'PLAN_IDEMPOTENCY_CONFLICT',
            'PLAN_CANCELLED',
            'PLAN_ABORTED',
            'ENVIRONMENT_STATE_PLANNED',
            'DEPLOYABILITY_CHECK_FAILED',
            'PROMOTION_RULE_UPSERTED',
            'PROMOTION_EVIDENCE_RECORDED',
            'PROTECTED_POLICY_UPSERTED',
            'APPROVAL_REQUESTED',
            'APPROVAL_APPROVED',
            'APPROVAL_REJECTED',
            'APPROVAL_EXPIRED',
            'DEPLOYMENT_LOCK_ACQUIRED',
            'DEPLOYMENT_LOCK_RELEASED',
            'DEPLOYMENT_LOCK_EXPIRED',
            'DEPLOYMENT_LOCK_STALE_RELEASED',
            'OVERRIDE_RECORDED',
            'GATE_DEFINITION_CREATED',
            'GATE_EVALUATED',
            'GATE_OVERRIDDEN',
            'GATE_RERUN_REQUESTED',
            'ROLLOUT_STARTED',
            'ROLLOUT_WAVE_STARTED',
            'ROLLOUT_WAVE_COMPLETED',
            'ROLLOUT_WAVE_FAILED',
            'ROLLOUT_PAUSED',
            'ROLLOUT_RESUMED',
            'ROLLOUT_ABORTED',
            'ROLLOUT_SUCCEEDED',
            'ROLLOUT_FAILED',
            'ROLLBACK_RECOMMENDED',
            'ROLLBACK_STARTED',
            'ROLLBACK_GATE_EVALUATED',
            'ROLLBACK_SUCCEEDED',
            'ROLLBACK_FAILED',
            'ROLLBACK_RETRY_REQUESTED',
            'ROLLBACK_MANUAL_INTERVENTION_REQUIRED',
            'ROLLBACK_ACKNOWLEDGED',
            'RECOVERY_ACTION_RECORDED',
            'ENVIRONMENT_STATE_UPDATED',
            'CONSISTENCY_VERIFIER_RUN',
            'CONSISTENCY_VERIFIER_FAILED'
        ));

alter table environment_deployment_states
    drop constraint environment_deployment_states_status_check,
    add constraint environment_deployment_states_status_check
        check (state_status in (
            'NEVER_DEPLOYED',
            'PLANNED',
            'DEPLOYING',
            'PARTIALLY_DEPLOYED',
            'DEPLOYED',
            'FAILED',
            'ROLLBACK_RECOMMENDED',
            'ROLLBACK_RUNNING',
            'ROLLED_BACK',
            'MANUAL_INTERVENTION_REQUIRED'
        ));

alter table environment_deployment_states
    add column previous_stable_artifact_id uuid references release_artifacts(id),
    add column last_rollout_execution_id uuid references rollout_executions(id);

create unique index uq_rollout_active_service_environment
    on rollout_executions(service_id, environment_id)
    where status in ('RUNNING', 'WAITING_FOR_GATES', 'PAUSED');

alter table rollback_recommendations
    add column metadata_json jsonb not null default '{}'::jsonb;

create table rollback_executions (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    rollback_recommendation_id uuid not null references rollback_recommendations(id),
    rollout_execution_id uuid not null references rollout_executions(id),
    deployment_plan_id uuid not null references deployment_plans(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    failed_artifact_id uuid not null references release_artifacts(id),
    target_artifact_id uuid references release_artifacts(id),
    status text not null,
    started_by text not null,
    reason text not null,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    failure_reason text,
    retry_count int not null default 0,
    manual_intervention_reason text,
    success_actor text,
    success_reason text,
    failure_actor text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(rollback_recommendation_id),
    constraint rollback_executions_status_check
        check (status in ('RUNNING', 'WAITING_FOR_GATES', 'SUCCEEDED', 'FAILED', 'MANUAL_INTERVENTION_REQUIRED', 'ABORTED')),
    constraint rollback_executions_started_by_not_blank check (length(trim(started_by)) > 0),
    constraint rollback_executions_reason_not_blank check (length(trim(reason)) > 0),
    constraint rollback_executions_retry_count_check check (retry_count >= 0)
);

create index idx_rollback_executions_project_id on rollback_executions(project_id);
create index idx_rollback_executions_rollout_id on rollback_executions(rollout_execution_id);
create index idx_rollback_executions_plan_id on rollback_executions(deployment_plan_id);
create index idx_rollback_executions_service_environment on rollback_executions(service_id, environment_id);
create index idx_rollback_executions_status on rollback_executions(status);

alter table environment_deployment_states
    add column last_rollback_execution_id uuid references rollback_executions(id);

alter table deployment_gate_attempts
    add column rollback_execution_id uuid references rollback_executions(id);

create index idx_deployment_gate_attempts_rollback_execution_id on deployment_gate_attempts(rollback_execution_id);

create table recovery_event_timeline (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    rollout_execution_id uuid references rollout_executions(id),
    rollback_execution_id uuid references rollback_executions(id),
    deployment_plan_id uuid references deployment_plans(id),
    event_type text not null,
    actor text not null,
    reason text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint recovery_event_timeline_actor_not_blank check (length(trim(actor)) > 0),
    constraint recovery_event_timeline_reason_not_blank check (length(trim(reason)) > 0)
);

create index idx_recovery_event_timeline_project_id on recovery_event_timeline(project_id);
create index idx_recovery_event_timeline_rollout_id on recovery_event_timeline(rollout_execution_id);
create index idx_recovery_event_timeline_rollback_id on recovery_event_timeline(rollback_execution_id);
