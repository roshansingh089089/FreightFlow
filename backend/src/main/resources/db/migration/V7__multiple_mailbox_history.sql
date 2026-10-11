-- Keep imported messages attached to their original mailbox when a user switches addresses.
do $$
declare old_constraint text;
begin
 select conname into old_constraint
 from pg_constraint
 where conrelid='mail_connections'::regclass
   and contype='u'
   and pg_get_constraintdef(oid) like 'UNIQUE (tenant_id, user_id, provider)%'
 limit 1;
 if old_constraint is not null then
  execute format('alter table mail_connections drop constraint %I',old_constraint);
 end if;
end $$;
create unique index uq_mail_connections_provider_address on mail_connections(tenant_id,user_id,provider,lower(email_address));
create unique index uq_mail_connections_gmail_user on mail_connections(tenant_id,user_id) where provider='GMAIL_API';
