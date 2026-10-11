-- Retain imported mail after it leaves INBOX while keeping the current Inbox view accurate.
alter table email_messages add column in_inbox boolean not null default true;
create index idx_email_current_imap_inbox
 on email_messages(tenant_id, connection_id, imap_uid_validity, received_at desc)
 where in_inbox and imap_uid is not null;
