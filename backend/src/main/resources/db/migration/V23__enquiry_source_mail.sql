alter table enquiries add column source_message_id uuid;
alter table enquiries add column source_thread_id uuid;
alter table enquiries add column source_connection_id uuid;

create unique index uq_enquiries_source_message
    on enquiries (tenant_id, source_message_id) where source_message_id is not null;

alter table enquiries add constraint fk_enquiry_source_mailbox
    foreign key (source_message_id, tenant_id, source_connection_id)
    references email_messages (id, tenant_id, connection_id);

alter table enquiries add constraint fk_enquiry_source_thread
    foreign key (source_thread_id, tenant_id, source_connection_id)
    references mail_threads (id, tenant_id, connection_id);
