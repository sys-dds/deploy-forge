create table deployment_projects (
    id uuid primary key,
    name text not null,
    slug text not null unique,
    description text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table deployable_services (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    name text not null,
    slug text not null,
    repository_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (project_id, slug)
);

create table deployment_environments (
    id uuid primary key,
    project_id uuid not null references deployment_projects(id),
    name text not null,
    environment_type text not null,
    protected_environment boolean not null default false,
    sort_order int not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (project_id, name),
    constraint deployment_environments_type_check
        check (environment_type in ('DEV', 'QA', 'STAGING', 'PROD'))
);

create index idx_deployable_services_project_id
    on deployable_services(project_id);

create index idx_deployment_environments_project_id
    on deployment_environments(project_id);

create index idx_deployment_environments_project_id_sort_order
    on deployment_environments(project_id, sort_order);
