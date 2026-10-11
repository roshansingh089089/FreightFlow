alter table outbound_messages add column reply_to_outbound_message_id uuid;

alter table outbound_messages add constraint fk_outbound_reply_parent_tenant
    foreign key (reply_to_outbound_message_id, tenant_id)
    references outbound_messages (id, tenant_id);
