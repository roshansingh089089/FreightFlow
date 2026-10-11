create table inbound_mail_attachments (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 connection_id uuid not null references mail_connections(id),
 message_id uuid not null references email_messages(id) on delete cascade,
 filename varchar(255) not null,
 media_type varchar(120) not null,
 size_bytes bigint not null check (size_bytes > 0 and size_bytes <= 10485760),
 storage_key varchar(255) not null unique,
 sha256 varchar(64) not null,
 created_at timestamptz not null default now()
);
create index idx_inbound_attachments_message on inbound_mail_attachments(tenant_id,connection_id,message_id);
