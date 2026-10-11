alter table email_messages
  add constraint fk_email_thread_mailbox
  foreign key (thread_id,tenant_id,connection_id)
  references mail_threads(id,tenant_id,connection_id);

alter table outbound_messages
  add constraint fk_outbound_thread_mailbox
  foreign key (thread_id,tenant_id,connection_id)
  references mail_threads(id,tenant_id,connection_id);

create unique index uq_email_message_tenant_mailbox on email_messages(id,tenant_id,connection_id);
alter table inbound_mail_attachments
  add constraint fk_inbound_attachment_mailbox
  foreign key (message_id,tenant_id,connection_id)
  references email_messages(id,tenant_id,connection_id) on delete cascade;

create unique index uq_outbound_message_tenant on outbound_messages(id,tenant_id);
alter table outbound_recipients
  add constraint fk_outbound_recipient_tenant
  foreign key (message_id,tenant_id)
  references outbound_messages(id,tenant_id) on delete cascade;
alter table outbound_attachments
  add constraint fk_outbound_attachment_tenant
  foreign key (message_id,tenant_id)
  references outbound_messages(id,tenant_id) on delete set null (message_id);
