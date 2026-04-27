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
            'ROLLBACK_RECOMMENDED'
        ));

create table rollout_executions (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    deployment_plan_id uuid not null references deployment_plans(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    artifact_id uuid not null references release_artifacts(id),
    strategy text not null,
    status text not null,
    started_by text not null,
    reason text not null,
    current_wave_number int,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    paused_at timestamptz,
    paused_by text,
    pause_reason text,
    resumed_at timestamptz,
    resumed_by text,
    resume_reason text,
    aborted_at timestamptz,
    aborted_by text,
    abort_reason text,
    failure_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(deployment_plan_id),
    constraint rollout_executions_strategy_check check (strategy in ('ALL_AT_ONCE', 'CANARY')),
    constraint rollout_executions_status_check check (status in ('NOT_STARTED', 'RUNNING', 'WAITING_FOR_GATES', 'PAUSED', 'SUCCEEDED', 'FAILED', 'ABORTED', 'ROLLBACK_RECOMMENDED')),
    constraint rollout_executions_started_by_not_blank check (length(trim(started_by)) > 0),
    constraint rollout_executions_reason_not_blank check (length(trim(reason)) > 0)
);

create index idx_rollout_executions_project_id on rollout_executions(project_id);
create index idx_rollout_executions_plan_id on rollout_executions(deployment_plan_id);
create index idx_rollout_executions_service_environment on rollout_executions(service_id, environment_id);
create index idx_rollout_executions_status on rollout_executions(status);

create table rollout_waves (
    id uuid primary key,
    rollout_execution_id uuid not null references rollout_executions(id),
    project_id uuid not null references deployment_projects(id),
    deployment_plan_id uuid not null references deployment_plans(id),
    wave_number int not null,
    traffic_percentage int not null,
    status text not null,
    started_at timestamptz,
    completed_at timestamptz,
    failure_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(rollout_execution_id, wave_number),
    constraint rollout_waves_status_check check (status in ('PENDING', 'RUNNING', 'WAITING_FOR_GATES', 'PASSED', 'FAILED', 'SKIPPED', 'ABORTED')),
    constraint rollout_waves_number_check check (wave_number > 0),
    constraint rollout_waves_traffic_check check (traffic_percentage > 0 and traffic_percentage <= 100)
);

create index idx_rollout_waves_execution_id on rollout_waves(rollout_execution_id);
create index idx_rollout_waves_project_id on rollout_waves(project_id);
create index idx_rollout_waves_plan_id on rollout_waves(deployment_plan_id);
create index idx_rollout_waves_status on rollout_waves(status);

alter table deployment_gate_attempts
    add column rollout_execution_id uuid references rollout_executions(id),
    add column rollout_wave_id uuid references rollout_waves(id);

create index idx_deployment_gate_attempts_rollout_execution_id on deployment_gate_attempts(rollout_execution_id);
create index idx_deployment_gate_attempts_rollout_wave_id on deployment_gate_attempts(rollout_wave_id);

create table rollback_recommendations (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    rollout_execution_id uuid not null references rollout_executions(id),
    deployment_plan_id uuid not null references deployment_plans(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    failed_artifact_id uuid not null references release_artifacts(id),
    recommended_artifact_id uuid references release_artifacts(id),
    recommendation_status text not null,
    reason text not null,
    created_at timestamptz not null default now(),
    acknowledged_at timestamptz,
    acknowledged_by text,
    acknowledgement_reason text,
    constraint rollback_recommendations_status_check check (recommendation_status in ('OPEN', 'ACKNOWLEDGED', 'SUPERSEDED')),
    constraint rollback_recommendations_reason_not_blank check (length(trim(reason)) > 0)
);

create unique index uq_rollback_recommendations_open_rollout on rollback_recommendations(rollout_execution_id) where recommendation_status = 'OPEN';
create index idx_rollback_recommendations_project_id on rollback_recommendations(project_id);
create index idx_rollback_recommendations_rollout_id on rollback_recommendations(rollout_execution_id);
create index idx_rollback_recommendations_status on rollback_recommendations(recommendation_status);
