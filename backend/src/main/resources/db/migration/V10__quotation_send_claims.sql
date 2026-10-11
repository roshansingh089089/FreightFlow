create table quotation_send_claims (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 quotation_id uuid not null references quotations(id),
 requested_by uuid not null references users(id),
 idempotency_key varchar(100) not null,
 status varchar(24) not null check (status in ('SENDING','SENT','NEEDS_REVIEW')),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 unique(tenant_id,quotation_id),
 unique(tenant_id,requested_by,idempotency_key)
);
