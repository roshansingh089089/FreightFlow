create table outbound_messages (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 created_by uuid not null references users(id),
 connection_id uuid references mail_connections(id),
 customer_id uuid references customers(id),
 quotation_id uuid references quotations(id),
 status varchar(24) not null,
 from_address varchar(255),
 reply_to varchar(255),
 subject varchar(998) not null default '',
 body_text text not null default '',
 message_id varchar(255),
 in_reply_to varchar(255),
 reference_headers text,
 idempotency_key varchar(100),
 send_attempts integer not null default 0,
 error_code varchar(60),
 created_at timestamptz not null,
 updated_at timestamptz not null,
 queued_at timestamptz,
 sent_at timestamptz,
 constraint ck_outbound_status check (status in ('DRAFT','QUEUED','SENDING','SENT','FAILED','NEEDS_REVIEW'))
);
create unique index uq_outbound_idempotency on outbound_messages(tenant_id,created_by,idempotency_key) where idempotency_key is not null;
create unique index uq_outbound_quotation_submitted on outbound_messages(tenant_id,quotation_id) where quotation_id is not null and status in ('QUEUED','SENDING','SENT','NEEDS_REVIEW');
create index idx_outbound_owner_status on outbound_messages(tenant_id,created_by,status,created_at desc);
create index idx_outbound_customer on outbound_messages(tenant_id,customer_id,created_at desc);
create index idx_outbound_quotation on outbound_messages(tenant_id,quotation_id,created_at desc);
create index idx_outbound_queue on outbound_messages(status,queued_at) where status='QUEUED';

create table outbound_recipients (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 message_id uuid not null references outbound_messages(id) on delete cascade,
 recipient_type varchar(3) not null check (recipient_type in ('TO','CC','BCC')),
 email varchar(255) not null,
 display_name varchar(150),
 unique(message_id,recipient_type,email)
);
create index idx_outbound_recipients_message on outbound_recipients(tenant_id,message_id);

create table outbound_attachments (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 message_id uuid references outbound_messages(id) on delete set null,
 uploaded_by uuid not null references users(id),
 filename varchar(255) not null,
 media_type varchar(120) not null,
 size_bytes bigint not null,
 storage_key varchar(255) not null unique,
 sha256 varchar(64) not null,
 created_at timestamptz not null,
 constraint ck_outbound_attachment_size check (size_bytes > 0 and size_bytes <= 10485760)
);
create index idx_outbound_attachments_tenant_message on outbound_attachments(tenant_id,message_id);
