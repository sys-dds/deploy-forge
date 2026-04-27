alter table release_artifact_evidence
    add column added_by text,
    add column reason text;

update release_artifact_evidence
set added_by = 'unknown',
    reason = 'Imported from earlier evidence schema'
where added_by is null;

alter table release_artifact_evidence
    alter column added_by set not null,
    add constraint release_artifact_evidence_added_by_not_blank check (length(trim(added_by)) > 0);

alter table deployment_plans
    add column aborted_at timestamptz,
    add column aborted_by text,
    add column abort_reason text;

alter table deployment_plans
    drop constraint deployment_plans_status_check,
    add constraint deployment_plans_status_check check (status in ('DRAFT', 'READY', 'CANCELLED', 'ABORTED'));

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
            'GATE_RERUN_REQUESTED'
        ));

create table environment_promotion_rules (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    target_environment_id uuid not null references deployment_environments(id),
    required_source_environment_id uuid references deployment_environments(id),
    requires_successful_source_deployment boolean not null default true,
    requires_approval boolean not null default false,
    allow_override boolean not null default true,
    created_by text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(project_id, target_environment_id),
    constraint environment_promotion_rules_created_by_not_blank check (length(trim(created_by)) > 0),
    constraint environment_promotion_rules_source_target_check
        check (required_source_environment_id is null or target_environment_id <> required_source_environment_id)
);

create index idx_environment_promotion_rules_project_id on environment_promotion_rules(project_id);
create index idx_environment_promotion_rules_target_environment_id on environment_promotion_rules(target_environment_id);
create index idx_environment_promotion_rules_required_source_environment_id on environment_promotion_rules(required_source_environment_id);

create table promotion_evidence (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid not null references deployable_services(id),
    artifact_id uuid not null references release_artifacts(id),
    source_environment_id uuid not null references deployment_environments(id),
    target_environment_id uuid references deployment_environments(id),
    evidence_type text not null,
    evidence_ref text not null,
    recorded_by text not null,
    reason text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique(artifact_id, source_environment_id, target_environment_id, evidence_type, evidence_ref),
    constraint promotion_evidence_type_check
        check (evidence_type in ('SUCCESSFUL_SOURCE_DEPLOYMENT', 'MANUAL_PROMOTION_APPROVAL', 'EXTERNAL_CHANGE_RECORD', 'RELEASE_MANAGER_OVERRIDE', 'OTHER')),
    constraint promotion_evidence_ref_not_blank check (length(trim(evidence_ref)) > 0),
    constraint promotion_evidence_recorded_by_not_blank check (length(trim(recorded_by)) > 0),
    constraint promotion_evidence_reason_not_blank check (length(trim(reason)) > 0),
    constraint promotion_evidence_source_target_check
        check (target_environment_id is null or source_environment_id <> target_environment_id)
);

create index idx_promotion_evidence_project_id on promotion_evidence(project_id);
create index idx_promotion_evidence_service_artifact on promotion_evidence(service_id, artifact_id);
create index idx_promotion_evidence_source_environment_id on promotion_evidence(source_environment_id);
create index idx_promotion_evidence_target_environment_id on promotion_evidence(target_environment_id);

create table protected_environment_policies (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    environment_id uuid not null references deployment_environments(id),
    require_approval boolean not null default false,
    required_approval_count int not null default 1,
    require_promotion_evidence boolean not null default false,
    allow_emergency_override boolean not null default true,
    max_risk_without_override text not null default 'MEDIUM',
    created_by text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(project_id, environment_id),
    constraint protected_environment_policies_required_count_check check (required_approval_count >= 0),
    constraint protected_environment_policies_created_by_not_blank check (length(trim(created_by)) > 0),
    constraint protected_environment_policies_max_risk_check check (max_risk_without_override in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

create table deployment_approval_requests (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    deployment_plan_id uuid not null references deployment_plans(id),
    environment_id uuid not null references deployment_environments(id),
    status text not null,
    required_approval_count int not null,
    approved_count int not null default 0,
    requested_by text not null,
    reason text not null,
    expires_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint deployment_approval_requests_status_check check (status in ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    constraint deployment_approval_requests_required_count_check check (required_approval_count >= 1),
    constraint deployment_approval_requests_approved_count_check check (approved_count >= 0),
    constraint deployment_approval_requests_requested_by_not_blank check (length(trim(requested_by)) > 0),
    constraint deployment_approval_requests_reason_not_blank check (length(trim(reason)) > 0)
);

create unique index uq_deployment_approval_requests_pending_plan on deployment_approval_requests(deployment_plan_id) where status = 'PENDING';
create index idx_deployment_approval_requests_project_id on deployment_approval_requests(project_id);
create index idx_deployment_approval_requests_plan_id on deployment_approval_requests(deployment_plan_id);
create index idx_deployment_approval_requests_status on deployment_approval_requests(status);

create table deployment_approval_decisions (
    id uuid primary key,
    approval_request_id uuid not null references deployment_approval_requests(id),
    decision text not null,
    decided_by text not null,
    reason text not null,
    created_at timestamptz not null default now(),
    unique(approval_request_id, decided_by),
    constraint deployment_approval_decisions_decision_check check (decision in ('APPROVE', 'REJECT')),
    constraint deployment_approval_decisions_decided_by_not_blank check (length(trim(decided_by)) > 0),
    constraint deployment_approval_decisions_reason_not_blank check (length(trim(reason)) > 0)
);

create index idx_deployment_approval_decisions_request_id on deployment_approval_decisions(approval_request_id);
create index idx_deployment_approval_decisions_decided_by on deployment_approval_decisions(decided_by);

create table deployment_locks (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    deployment_plan_id uuid not null references deployment_plans(id),
    status text not null,
    lock_owner text not null,
    reason text not null,
    fencing_token bigint not null,
    acquired_at timestamptz not null default now(),
    expires_at timestamptz not null,
    released_at timestamptz,
    released_by text,
    release_reason text,
    constraint deployment_locks_status_check check (status in ('ACTIVE', 'RELEASED', 'EXPIRED')),
    constraint deployment_locks_owner_not_blank check (length(trim(lock_owner)) > 0),
    constraint deployment_locks_reason_not_blank check (length(trim(reason)) > 0),
    constraint deployment_locks_fencing_token_check check (fencing_token > 0)
);

create unique index uq_deployment_locks_active_service_env on deployment_locks(service_id, environment_id) where status = 'ACTIVE';
create sequence deployment_lock_fencing_token_seq;
create index idx_deployment_locks_project_id on deployment_locks(project_id);
create index idx_deployment_locks_service_environment on deployment_locks(service_id, environment_id);
create index idx_deployment_locks_plan_id on deployment_locks(deployment_plan_id);
create index idx_deployment_locks_status on deployment_locks(status);
create index idx_deployment_locks_expires_at on deployment_locks(expires_at);

create table deployment_overrides (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    deployment_plan_id uuid not null references deployment_plans(id),
    override_type text not null,
    actor text not null,
    reason text not null,
    risk_acknowledgement text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint deployment_overrides_type_check check (override_type in ('PROMOTION_EVIDENCE', 'APPROVAL', 'GATE', 'LOCK', 'RISK_POLICY')),
    constraint deployment_overrides_actor_not_blank check (length(trim(actor)) > 0),
    constraint deployment_overrides_reason_not_blank check (length(trim(reason)) > 0),
    constraint deployment_overrides_risk_ack_not_blank check (length(trim(risk_acknowledgement)) > 0)
);

create index idx_deployment_overrides_project_id on deployment_overrides(project_id);
create index idx_deployment_overrides_plan_id on deployment_overrides(deployment_plan_id);
create index idx_deployment_overrides_type on deployment_overrides(override_type);

create table deployment_gate_definitions (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    environment_id uuid references deployment_environments(id),
    name text not null,
    gate_type text not null,
    required boolean not null default true,
    enabled boolean not null default true,
    timeout_seconds int not null default 30,
    config_json jsonb not null default '{}'::jsonb,
    created_by text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(project_id, environment_id, name),
    constraint deployment_gate_definitions_type_check check (gate_type in ('HTTP_HEALTH', 'SYNTHETIC_CHECK', 'METRIC_THRESHOLD')),
    constraint deployment_gate_definitions_name_not_blank check (length(trim(name)) > 0),
    constraint deployment_gate_definitions_created_by_not_blank check (length(trim(created_by)) > 0),
    constraint deployment_gate_definitions_timeout_check check (timeout_seconds > 0)
);

create index idx_deployment_gate_definitions_project_id on deployment_gate_definitions(project_id);
create index idx_deployment_gate_definitions_environment_id on deployment_gate_definitions(environment_id);
create index idx_deployment_gate_definitions_type on deployment_gate_definitions(gate_type);
create index idx_deployment_gate_definitions_enabled on deployment_gate_definitions(enabled);

create table deployment_gate_attempts (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    deployment_plan_id uuid not null references deployment_plans(id),
    gate_definition_id uuid not null references deployment_gate_definitions(id),
    attempt_number int not null,
    status text not null,
    observed_json jsonb not null default '{}'::jsonb,
    result_summary text,
    failure_reason text,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    overridden_at timestamptz,
    overridden_by text,
    override_reason text,
    unique(deployment_plan_id, gate_definition_id, attempt_number),
    constraint deployment_gate_attempts_status_check check (status in ('PENDING', 'RUNNING', 'PASSED', 'FAILED', 'TIMED_OUT', 'SKIPPED', 'OVERRIDDEN')),
    constraint deployment_gate_attempts_number_check check (attempt_number > 0)
);

create index idx_deployment_gate_attempts_project_id on deployment_gate_attempts(project_id);
create index idx_deployment_gate_attempts_plan_id on deployment_gate_attempts(deployment_plan_id);
create index idx_deployment_gate_attempts_gate_definition_id on deployment_gate_attempts(gate_definition_id);
create index idx_deployment_gate_attempts_status on deployment_gate_attempts(status);
