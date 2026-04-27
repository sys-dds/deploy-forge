create table operator_recovery_actions (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    action_type text not null,
    target_type text not null,
    target_id uuid not null,
    actor text not null,
    reason text not null,
    risk_acknowledgement text,
    metadata_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint operator_recovery_actions_type_check check (action_type in (
        'FORCE_RELEASE_STALE_LEASE','FORCE_PARK_COMMAND','FORCE_RETRY_COMMAND',
        'MARK_COMMAND_MANUALLY_RESOLVED','MARK_ROLLOUT_MANUAL_INTERVENTION',
        'MARK_ROLLBACK_MANUAL_INTERVENTION','RECORD_INVESTIGATION_NOTE'
    )),
    constraint operator_recovery_actions_target_type_check check (target_type in ('COMMAND','ROLLOUT','ROLLBACK','LOCK','DRIFT','REPAIR_PLAN')),
    constraint operator_recovery_actions_actor_not_blank check (length(trim(actor)) > 0),
    constraint operator_recovery_actions_reason_not_blank check (length(trim(reason)) > 0)
);

create index idx_operator_recovery_actions_project_id on operator_recovery_actions(project_id);
create index idx_operator_recovery_actions_target on operator_recovery_actions(target_type, target_id);
create index idx_operator_recovery_actions_action_type on operator_recovery_actions(action_type);
