create table if not exists organizations (
    id uuid primary key,
    organization_key varchar(80) not null unique,
    name varchar(160) not null,
    description varchar(600),
    plan varchar(80) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

alter table projects
    add column if not exists organization_key varchar(80) not null default 'demo-enterprise';

alter table code_repositories
    add column if not exists organization_key varchar(80) not null default 'demo-enterprise';

alter table reviews
    add column if not exists organization_key varchar(80) not null default 'demo-enterprise';

create table if not exists review_policies (
    id uuid primary key,
    organization_key varchar(80) not null,
    project_key varchar(80) not null,
    name varchar(160) not null,
    block_severity varchar(10) not null,
    fail_on_p0 boolean not null,
    require_tests_for_api_changes boolean not null,
    enable_bug_logic boolean not null,
    enable_security boolean not null,
    enable_code_quality boolean not null,
    enable_test_coverage boolean not null,
    enable_llm_review boolean not null,
    max_diff_chars integer not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists audit_logs (
    id uuid primary key,
    organization_key varchar(80) not null,
    actor_username varchar(120) not null,
    actor_role varchar(80) not null,
    action varchar(120) not null,
    resource_type varchar(80) not null,
    resource_id varchar(160),
    summary text not null,
    created_at timestamp not null
);

create index if not exists idx_projects_org_created
    on projects(organization_key, created_at);

create index if not exists idx_repositories_org_project
    on code_repositories(organization_key, project_id);

create index if not exists idx_reviews_org_status_created
    on reviews(organization_key, status, created_at desc);

create index if not exists idx_review_policies_org_project
    on review_policies(organization_key, project_key);

create index if not exists idx_audit_logs_org_created
    on audit_logs(organization_key, created_at desc);
