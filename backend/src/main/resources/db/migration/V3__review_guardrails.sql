alter table reviews
    add column if not exists idempotency_key varchar(120);

create unique index if not exists idx_reviews_org_idempotency
    on reviews(organization_key, idempotency_key);

create index if not exists idx_reviews_org_active
    on reviews(organization_key, status, updated_at desc);
