create table mail_threads (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 connection_id uuid not null references mail_connections(id),
 customer_id uuid references customers(id),
 normalized_subject varchar(998) not null default '',
 created_at timestamptz not null,
 last_activity_at timestamptz not null,
 unique(id, tenant_id, connection_id)
);
create index idx_mail_threads_mailbox_activity on mail_threads(tenant_id,connection_id,last_activity_at desc);
create index idx_mail_threads_customer on mail_threads(tenant_id,customer_id,last_activity_at desc);

alter table email_messages add column thread_id uuid references mail_threads(id);
alter table email_messages add column reply_to_email text;
alter table email_messages add column cc_email text;
alter table email_messages add column imap_uid bigint;
alter table email_messages add column imap_uid_validity bigint;
create unique index uq_email_imap_uid on email_messages(connection_id,imap_uid_validity,imap_uid)
 where imap_uid is not null and imap_uid_validity is not null;
create index idx_email_thread on email_messages(tenant_id,thread_id,received_at);
create index idx_email_headers_mailbox on email_messages(tenant_id,connection_id,rfc_message_id)
 where rfc_message_id is not null;

alter table outbound_messages add column thread_id uuid references mail_threads(id);
alter table outbound_messages add column reply_to_message_id uuid references email_messages(id);
alter table outbound_messages add column compose_mode varchar(12) not null default 'NEW';
alter table outbound_messages add constraint ck_outbound_compose_mode
 check (compose_mode in ('NEW','REPLY','REPLY_ALL'));
create index idx_outbound_thread on outbound_messages(tenant_id,thread_id,created_at);
create unique index uq_outbound_message_id on outbound_messages(connection_id,message_id)
 where message_id is not null;

alter table mail_connections add column imap_inbox_uid_validity bigint;
alter table mail_connections add column imap_inbox_last_uid bigint;
alter table mail_connections add column sync_error_code varchar(60);
alter table mail_connections add column sync_lease_until timestamptz;

-- Preserve historical messages. Without RFC reference evidence, each old message starts
-- its own thread; subject similarity is deliberately not used for migration grouping.
insert into mail_threads(id,tenant_id,connection_id,normalized_subject,created_at,last_activity_at)
 select id,tenant_id,connection_id,left(coalesce(subject,''),998),created_at,received_at
 from email_messages;
update email_messages set thread_id=id where thread_id is null;
insert into mail_threads(id,tenant_id,connection_id,customer_id,normalized_subject,created_at,last_activity_at)
 select id,tenant_id,connection_id,customer_id,left(subject,998),created_at,coalesce(sent_at,created_at)
 from outbound_messages where connection_id is not null;
update outbound_messages set thread_id=id where connection_id is not null and thread_id is null;
