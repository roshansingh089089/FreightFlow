create table mail_sent_copies (
 message_id uuid primary key,
 tenant_id uuid not null references tenants(id),
 user_id uuid not null references users(id),
 connection_id uuid not null references mail_connections(id),
 rfc_message_id varchar(255) not null,
 mime_bytes bytea,
 status varchar(16) not null default 'PREPARED',
 attempts integer not null default 0,
 folder_name varchar(255),
 error_code varchar(60),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 next_attempt_at timestamptz not null default now(),
 synced_at timestamptz,
 constraint ck_mail_sent_copy_status check (status in ('PREPARED','SYNCING','RETRY','SYNCED')),
 constraint ck_mail_sent_copy_mime check (status='SYNCED' or mime_bytes is not null),
 constraint fk_sent_copy_message_tenant foreign key (message_id,tenant_id)
  references outbound_messages(id,tenant_id) on delete cascade
);
create index idx_mail_sent_copies_pending on mail_sent_copies(status,next_attempt_at)
 where status in ('PREPARED','RETRY');
