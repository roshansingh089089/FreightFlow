-- Merge historical messages only when a unique RFC parent exists in the same mailbox.
-- Subject text is never used as evidence of a shared conversation.
do $$
declare changed integer;
begin
  loop
    with unique_inbound_parent as (
      select tenant_id,connection_id,rfc_message_id,min(thread_id::text)::uuid as thread_id,
             min(received_at) as parent_at
      from email_messages
      where rfc_message_id is not null and thread_id is not null
      group by tenant_id,connection_id,rfc_message_id
      having count(*)=1
    )
    update email_messages child
      set thread_id=parent.thread_id
      from unique_inbound_parent parent
      where child.tenant_id=parent.tenant_id
        and child.connection_id=parent.connection_id
        and btrim(child.in_reply_to)=parent.rfc_message_id
        and parent.parent_at<child.received_at
        and child.thread_id is distinct from parent.thread_id;
    get diagnostics changed = row_count;
    exit when changed=0;
  end loop;
end $$;

with unique_inbound_parent as (
  select tenant_id,connection_id,rfc_message_id,min(thread_id::text)::uuid as thread_id,
         min(received_at) as parent_at
  from email_messages
  where rfc_message_id is not null and thread_id is not null
  group by tenant_id,connection_id,rfc_message_id
  having count(*)=1
)
update outbound_messages child
  set thread_id=parent.thread_id,compose_mode='REPLY'
  from unique_inbound_parent parent
  where child.tenant_id=parent.tenant_id
    and child.connection_id=parent.connection_id
    and btrim(child.in_reply_to)=parent.rfc_message_id
    and parent.parent_at<child.created_at
    and child.thread_id is distinct from parent.thread_id;

with unique_outbound_parent as (
  select tenant_id,connection_id,message_id,min(thread_id::text)::uuid as thread_id,
         min(coalesce(sent_at,created_at)) as parent_at
  from outbound_messages
  where message_id is not null and thread_id is not null
  group by tenant_id,connection_id,message_id
  having count(*)=1
)
update email_messages child
  set thread_id=parent.thread_id
  from unique_outbound_parent parent
  where child.tenant_id=parent.tenant_id
    and child.connection_id=parent.connection_id
    and btrim(child.in_reply_to)=parent.message_id
    and parent.parent_at<child.received_at
    and child.thread_id is distinct from parent.thread_id;

update mail_threads thread
set last_activity_at=latest.at
from (
  select thread_id,max(activity_at) as at
  from (
    select thread_id,received_at as activity_at from email_messages where thread_id is not null
    union all
    select thread_id,coalesce(sent_at,created_at) from outbound_messages where thread_id is not null
  ) activity
  group by thread_id
) latest
where thread.id=latest.thread_id;

delete from mail_threads thread
where not exists (select 1 from email_messages message where message.thread_id=thread.id)
  and not exists (select 1 from outbound_messages message where message.thread_id=thread.id);
