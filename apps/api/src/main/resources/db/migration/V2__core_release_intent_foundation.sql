alter table deployment_projects
    add column owner_team text,
    add column lifecycle_status text not null default 'ACTIVE',
    add constraint deployment_projects_lifecycle_status_check
        check (lifecycle_status in ('ACTIVE', 'ARCHIVED'));

alter table deployable_services
    add column service_tier text not null default 'STANDARD',
    add column runtime_kind text not null default 'SERVICE',
    add column lifecycle_status text not null default 'ACTIVE',
    add constraint deployable_services_service_tier_check
        check (service_tier in ('STANDARD', 'CRITICAL')),
    add constraint deployable_services_runtime_kind_check
        check (runtime_kind in ('SERVICE', 'WORKER', 'FRONTEND', 'JOB')),
    add constraint deployable_services_lifecycle_status_check
        check (lifecycle_status in ('ACTIVE', 'ARCHIVED'));

alter table deployment_environments
    add column external_target_id text,
    add column requires_approval boolean not null default false,
    add column lifecycle_status text not null default 'ACTIVE',
    add constraint deployment_environments_lifecycle_status_check
        check (lifecycle_status in ('ACTIVE', 'ARCHIVED'));

create table release_artifacts (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid not null references deployable_services(id),
    version text not null,
    git_sha text not null,
    image_digest text not null,
    build_number text,
    source_branch text,
    commit_message text,
    created_by text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    readiness_status text not null default 'READY',
    created_at timestamptz not null default now(),
    unique (service_id, version),
    constraint release_artifacts_version_not_blank check (length(trim(version)) > 0),
    constraint release_artifacts_git_sha_not_blank check (length(trim(git_sha)) > 0),
    constraint release_artifacts_image_digest_not_blank check (length(trim(image_digest)) > 0),
    constraint release_artifacts_created_by_not_blank check (length(trim(created_by)) > 0),
    constraint release_artifacts_readiness_status_check check (readiness_status in ('READY', 'BLOCKED'))
);

create index idx_release_artifacts_project_id on release_artifacts(project_id);
create index idx_release_artifacts_service_id on release_artifacts(service_id);
create index idx_release_artifacts_service_id_created_at on release_artifacts(service_id, created_at);
create index idx_release_artifacts_git_sha on release_artifacts(git_sha);
create index idx_release_artifacts_image_digest on release_artifacts(image_digest);
create index idx_release_artifacts_readiness_status on release_artifacts(readiness_status);

create table release_artifact_evidence (
    id uuid primary key,
    artifact_id uuid not null references release_artifacts(id),
    evidence_type text not null,
    evidence_ref text not null,
    evidence_sha text,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    unique (artifact_id, evidence_type, evidence_ref),
    constraint release_artifact_evidence_type_check
        check (evidence_type in ('BUILD_LOG', 'TEST_REPORT', 'SBOM', 'SECURITY_SCAN', 'IMAGE_SCAN', 'CHANGELOG', 'OTHER')),
    constraint release_artifact_evidence_ref_not_blank check (length(trim(evidence_ref)) > 0)
);

create index idx_release_artifact_evidence_artifact_id on release_artifact_evidence(artifact_id);
create index idx_release_artifact_evidence_type on release_artifact_evidence(evidence_type);

create table deployment_plans (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid not null references deployable_services(id),
    artifact_id uuid not null references release_artifacts(id),
    target_environment_id uuid not null references deployment_environments(id),
    strategy text not null,
    status text not null,
    risk_level text not null,
    reason text not null,
    requested_by text not null,
    idempotency_key text not null,
    request_hash text not null,
    evidence_snapshot_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    cancelled_at timestamptz,
    cancelled_by text,
    cancel_reason text,
    unique (project_id, idempotency_key),
    constraint deployment_plans_strategy_check check (strategy in ('ALL_AT_ONCE', 'CANARY')),
    constraint deployment_plans_status_check check (status in ('DRAFT', 'READY', 'CANCELLED')),
    constraint deployment_plans_risk_level_check check (risk_level in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    constraint deployment_plans_reason_not_blank check (length(trim(reason)) > 0),
    constraint deployment_plans_requested_by_not_blank check (length(trim(requested_by)) > 0),
    constraint deployment_plans_request_hash_not_blank check (length(trim(request_hash)) > 0)
);

create index idx_deployment_plans_project_id on deployment_plans(project_id);
create index idx_deployment_plans_service_id on deployment_plans(service_id);
create index idx_deployment_plans_artifact_id on deployment_plans(artifact_id);
create index idx_deployment_plans_target_environment_id on deployment_plans(target_environment_id);
create index idx_deployment_plans_status on deployment_plans(status);
create index idx_deployment_plans_project_id_created_at on deployment_plans(project_id, created_at);
create index idx_deployment_plans_risk_level on deployment_plans(risk_level);

create table environment_deployment_states (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid not null references deployable_services(id),
    environment_id uuid not null references deployment_environments(id),
    current_artifact_id uuid references release_artifacts(id),
    last_deployment_plan_id uuid references deployment_plans(id),
    state_status text not null,
    updated_at timestamptz not null default now(),
    unique (service_id, environment_id),
    constraint environment_deployment_states_status_check
        check (state_status in ('NEVER_DEPLOYED', 'PLANNED', 'DEPLOYED', 'FAILED', 'ROLLED_BACK'))
);

create table deployment_intent_events (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    deployment_plan_id uuid references deployment_plans(id),
    service_id uuid references deployable_services(id),
    environment_id uuid references deployment_environments(id),
    artifact_id uuid references release_artifacts(id),
    event_type text not null,
    actor text not null,
    reason text,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint deployment_intent_events_type_check
        check (event_type in (
            'ARTIFACT_REGISTERED',
            'ARTIFACT_EVIDENCE_ADDED',
            'PLAN_CREATED',
            'PLAN_IDEMPOTENT_REPLAYED',
            'PLAN_IDEMPOTENCY_CONFLICT',
            'PLAN_CANCELLED',
            'ENVIRONMENT_STATE_PLANNED',
            'DEPLOYABILITY_CHECK_FAILED'
        )),
    constraint deployment_intent_events_actor_not_blank check (length(trim(actor)) > 0)
);

create index idx_deployment_intent_events_project_id_created_at on deployment_intent_events(project_id, created_at);
create index idx_deployment_intent_events_plan_id_created_at on deployment_intent_events(deployment_plan_id, created_at);
create index idx_deployment_intent_events_type_created_at on deployment_intent_events(event_type, created_at);
