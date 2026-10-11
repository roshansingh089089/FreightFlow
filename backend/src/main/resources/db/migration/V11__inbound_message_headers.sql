alter table email_messages add column if not exists rfc_message_id varchar(255);
alter table email_messages add column if not exists in_reply_to varchar(255);
alter table email_messages add column if not exists reference_headers text;
create index if not exists idx_email_messages_rfc_id on email_messages(tenant_id,rfc_message_id) where rfc_message_id is not null;
