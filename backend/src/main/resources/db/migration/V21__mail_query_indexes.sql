create index idx_email_mailbox_received
  on email_messages(tenant_id,connection_id,received_at desc);
create index idx_email_mailbox_read_received
  on email_messages(tenant_id,connection_id,is_read,received_at desc);
create index idx_outbound_mailbox_activity
  on outbound_messages(tenant_id,created_by,connection_id,created_at desc);
create index idx_mail_sync_due
  on mail_connections(last_synced_at)
  where active=true and provider='IMAP_SMTP' and status='CONNECTED';
