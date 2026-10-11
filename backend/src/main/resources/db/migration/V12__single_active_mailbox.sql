do $$
begin
 if exists (
  select 1 from mail_connections
  where active = true
  group by tenant_id,user_id
  having count(*) > 1
 ) then
  raise exception 'Multiple active mail connections exist for at least one user. Resolve these records before applying V12.';
 end if;
end $$;

create unique index uq_mail_connections_one_active_user
 on mail_connections(tenant_id,user_id)
 where active = true;
