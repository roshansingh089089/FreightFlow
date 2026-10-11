-- One-time repair for the verified local development mailbox only.
-- Take a full pg_dump backup first. Run with psql -X -v ON_ERROR_STOP=1.
-- The exact IDs, legacy provider hashes, tenant, connection, UIDVALIDITY and UID
-- prevent this script from treating matching subjects as duplicate evidence.
begin;
select pg_advisory_xact_lock(hashtext('freightflow-local-hostinger-mail-repair'));

create temp table mail_duplicate_repair (
  canonical_id uuid primary key,
  duplicate_id uuid unique not null,
  uid bigint not null,
  legacy_provider_id text not null
) on commit drop;
insert into mail_duplicate_repair values
 ('11ef784e-1ed7-438d-b95a-466b2af25747','842e9a20-1a59-47f6-84cf-84ca96a60a6a',1,'mid:aad7338be1dc8d5f86ca2cfc1a84b32d6eb521e4c0c09404bcaef7d9bf99f9d6'),
 ('1f1e047b-82aa-49a9-b1c4-e3e49e88a99c','6d537eba-1db1-4ebf-b639-37ee43df6de2',2,'mid:bf2230b1fe7eb3a639bac12f25ec20849fa1f4dbd334bd4038cfb4a0826164f3'),
 ('453072b4-8f75-4977-8f08-d938d912c91f','d972d25e-5ea2-48dd-af68-17acf62743b4',3,'mid:5b2d859f36fe4f7316ed76a703005f0ce475beb9da8d0bb60c7a958966a9639f');

do $$
begin
  if current_database() <> 'freightflow' or current_user <> 'freightflow' then
    raise exception 'Unexpected database or role';
  end if;
  if (select count(*) from mail_duplicate_repair m
      join email_messages c on c.id=m.canonical_id
      join email_messages d on d.id=m.duplicate_id
      where c.tenant_id='a335c6df-f71d-4ac1-a5d9-a89664d9183a'
        and d.tenant_id=c.tenant_id
        and c.connection_id='72b993b0-d56f-4fa2-b565-d360e5a71604'
        and d.connection_id=c.connection_id
        and c.imap_uid_validity=1791517077 and c.imap_uid=m.uid
        and d.imap_uid is null and d.imap_uid_validity is null
        and d.provider_message_id=m.legacy_provider_id
        and c.from_email=d.from_email and c.subject=d.subject
        and c.thread_id is not null and d.thread_id=m.duplicate_id) <> 3 then
    raise exception 'Duplicate preflight failed; no rows repaired';
  end if;
  if exists (select 1 from customer_mail_links l
      join mail_duplicate_repair m on l.message_id=m.duplicate_id
      join customer_mail_links existing on existing.tenant_id=l.tenant_id
        and existing.message_id=m.canonical_id) then
    raise exception 'Canonical customer link already exists; manual review required';
  end if;
end $$;

update customer_mail_links l set message_id=m.canonical_id
 from mail_duplicate_repair m where l.message_id=m.duplicate_id;
update inbound_mail_attachments a set message_id=m.canonical_id
 from mail_duplicate_repair m where a.message_id=m.duplicate_id;
update outbound_messages o set reply_to_message_id=m.canonical_id
 from mail_duplicate_repair m where o.reply_to_message_id=m.duplicate_id;

-- Preserve thread membership for any later replies that referenced a legacy thread.
update email_messages e set thread_id=c.thread_id
 from mail_duplicate_repair m
 join email_messages c on c.id=m.canonical_id
 join email_messages d on d.id=m.duplicate_id
 where e.thread_id=d.thread_id and e.id<>d.id;
update outbound_messages o set thread_id=c.thread_id
 from mail_duplicate_repair m
 join email_messages c on c.id=m.canonical_id
 join email_messages d on d.id=m.duplicate_id
 where o.thread_id=d.thread_id;

do $$
begin
  if exists(select 1 from information_schema.columns
      where table_name='enquiries' and column_name='source_message_id') then
    execute 'update enquiries e set source_message_id=m.canonical_id '
      || 'from mail_duplicate_repair m where e.source_message_id=m.duplicate_id';
  end if;
end $$;

delete from email_messages e using mail_duplicate_repair m
 where e.id=m.duplicate_id
 returning e.id as repaired_duplicate_id;
delete from mail_threads t using mail_duplicate_repair m
 where t.id=m.duplicate_id
   and not exists(select 1 from email_messages e where e.thread_id=t.id)
   and not exists(select 1 from outbound_messages o where o.thread_id=t.id);

do $$
begin
  if exists(select 1 from email_messages e join mail_duplicate_repair m on e.id=m.duplicate_id)
     or (select count(*) from email_messages e join mail_duplicate_repair m on e.id=m.canonical_id)<>3 then
    raise exception 'Repair postcondition failed; rolling back';
  end if;
end $$;
commit;
