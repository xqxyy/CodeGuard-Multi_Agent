create table if not exists projects (
    id uuid primary key,
    project_key varchar(80) not null unique,
    name varchar(160) not null,
    description varchar(600),
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists code_repositories (
    id uuid primary key,
    project_id uuid not null,
    repository_name varchar(180) not null,
    provider varchar(80) not null,
    remote_url varchar(600),
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_code_repositories_project
        foreign key (project_id) references projects(id)
);

create table if not exists reviews (
    id uuid primary key,
    project_key varchar(80) not null default 'default',
    repository_name varchar(180) not null default 'manual-diff',
    source_type varchar(40) not null default 'MANUAL',
    source_url varchar(600),
    title varchar(200) not null,
    status varchar(40) not null,
    diff_text text not null,
    markdown text,
    error_message text,
    recommendation varchar(40),
    risk_score integer not null,
    files_changed integer not null,
    additions integer not null,
    deletions integer not null,
    enable_bug_logic boolean not null default true,
    enable_security boolean not null default true,
    enable_code_quality boolean not null default true,
    enable_test_coverage boolean not null default true,
    enable_llm_review boolean not null default true,
    fail_on_p0 boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists review_issues (
    id uuid primary key,
    review_id uuid not null,
    agent_type varchar(40) not null,
    tag varchar(40) not null,
    severity varchar(10) not null,
    file_path varchar(600),
    line_number integer,
    title varchar(240) not null,
    detail text not null,
    suggestion text not null,
    evidence text,
    created_at timestamp not null,
    constraint fk_review_issues_review
        foreign key (review_id) references reviews(id)
);

create table if not exists agent_traces (
    id uuid primary key,
    review_id uuid not null,
    agent_type varchar(40) not null,
    status varchar(40) not null,
    input_summary text not null,
    output_summary text,
    skip_reason text,
    duration_ms bigint not null,
    started_at timestamp not null,
    ended_at timestamp not null,
    prompt text,
    raw_output text,
    model_name varchar(120),
    provider varchar(80),
    prompt_tokens integer,
    completion_tokens integer,
    error_message text,
    constraint fk_agent_traces_review
        foreign key (review_id) references reviews(id)
);

create index if not exists idx_reviews_project_created
    on reviews(project_key, created_at desc);

create index if not exists idx_review_issues_review
    on review_issues(review_id);

create index if not exists idx_agent_traces_review
    on agent_traces(review_id);
