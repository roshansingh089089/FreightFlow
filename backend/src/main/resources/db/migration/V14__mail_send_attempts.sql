create table mail_send_attempts (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 message_id uuid not null references outbound_messages(id) on delete cascade,
 attempt_number integer not null,
 status varchar(24) not null check (status in ('SENDING','ACCEPTED','UNKNOWN')),
 error_code varchar(60),
 started_at timestamptz not null default now(),
 completed_at timestamptz,
 unique(message_id,attempt_number)
);
create index idx_mail_send_attempts_tenant_message on mail_send_attempts(tenant_id,message_id,started_at desc);
