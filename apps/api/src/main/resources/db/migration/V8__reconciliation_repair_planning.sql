create table reconciliation_policies (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid references deployable_services(id),
    environment_id uuid references deployment_environments(id),
    enabled boolean not null default true,
    auto_create_repair_intents boolean not null default false,
    require_approval_for_repair boolean not null default true,
    max_auto_repair_severity text not null default 'WARNING',
    created_by text not null,
    reason text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique(project_id, service_id, environment_id),
    constraint reconciliation_policies_severity_check check (max_auto_repair_severity in ('INFO','WARNING','CRITICAL')),
    constraint reconciliation_policies_created_by_not_blank check (length(trim(created_by)) > 0),
    constraint reconciliation_policies_reason_not_blank check (length(trim(reason)) > 0)
);

create table reconciliation_runs (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    service_id uuid references deployable_services(id),
    environment_id uuid references deployment_environments(id),
    status text not null,
    requested_by text not null,
    reason text not null,
    idempotency_key text not null,
    request_hash text not null,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    summary_json jsonb not null default '{}'::jsonb,
    unique(project_id, idempotency_key),
    constraint reconciliation_runs_status_check check (status in ('RUNNING','SUCCEEDED','FAILED')),
    constraint reconciliation_runs_requested_by_not_blank check (length(trim(requested_by)) > 0),
    constraint reconciliation_runs_reason_not_blank check (length(trim(reason)) > 0)
);

create table reconciliation_issues (
    id uuid primary key,
    reconciliation_run_id uuid not null references reconciliation_runs(id),
    project_id uuid not null references deployment_projects(id),
    drift_finding_id uuid references drift_findings(id),
    issue_type text not null,
    severity text not null,
    message text not null,
    recommended_action text not null,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint reconciliation_issues_severity_check check (severity in ('INFO','WARNING','CRITICAL')),
    constraint reconciliation_issues_message_not_blank check (length(trim(message)) > 0)
);

create table repair_plans (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    reconciliation_run_id uuid references reconciliation_runs(id),
    drift_finding_id uuid references drift_findings(id),
    plan_type text not null,
    status text not null,
    requires_approval boolean not null default true,
    evidence_snapshot_json jsonb not null default '{}'::jsonb,
    requested_by text not null,
    reason text not null,
    approved_at timestamptz,
    approved_by text,
    approval_reason text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint repair_plans_type_check check (plan_type in ('REDEPLOY_DESIRED_ARTIFACT','ROLLBACK_TO_STABLE','MANUAL_INTERVENTION','ACCEPT_ACTUAL_AS_DESIRED','INVESTIGATE')),
    constraint repair_plans_status_check check (status in ('PROPOSED','APPROVED','REJECTED','EXECUTION_RECOMMENDED','CANCELLED')),
    constraint repair_plans_requested_by_not_blank check (length(trim(requested_by)) > 0),
    constraint repair_plans_reason_not_blank check (length(trim(reason)) > 0)
);

create index idx_reconciliation_policies_project_id on reconciliation_policies(project_id);
create index idx_reconciliation_policies_service_environment on reconciliation_policies(service_id, environment_id);
create index idx_reconciliation_runs_project_id on reconciliation_runs(project_id);
create index idx_reconciliation_runs_service_environment on reconciliation_runs(service_id, environment_id);
create index idx_reconciliation_runs_status on reconciliation_runs(status);
create index idx_reconciliation_issues_run_id on reconciliation_issues(reconciliation_run_id);
create index idx_reconciliation_issues_drift_finding_id on reconciliation_issues(drift_finding_id);
create index idx_repair_plans_project_status on repair_plans(project_id, status);
create index idx_repair_plans_run_id on repair_plans(reconciliation_run_id);
create index idx_repair_plans_drift_finding_id on repair_plans(drift_finding_id);
