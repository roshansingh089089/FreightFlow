alter table outbound_messages add column quotation_send_kind varchar(12);
update outbound_messages set quotation_send_kind='PRIMARY'
 where quotation_id is not null and status in ('QUEUED','SENDING','SENT','NEEDS_REVIEW');
alter table outbound_messages add constraint ck_quotation_send_kind
 check (quotation_send_kind is null or quotation_send_kind in ('PRIMARY','FOLLOW_UP'));
drop index if exists uq_outbound_quotation_submitted;
create unique index uq_outbound_primary_quotation_send
 on outbound_messages(tenant_id,quotation_id)
 where quotation_id is not null and quotation_send_kind='PRIMARY'
   and status in ('QUEUED','SENDING','SENT','NEEDS_REVIEW');
