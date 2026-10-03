create table ai_interaction_log (
 id uuid primary key,
 tenant_id uuid not null,
 user_id uuid not null,
 action_type varchar(60) not null,
 related_entity_type varchar(40),
 related_entity_id uuid,
 status varchar(30) not null,
 created_at timestamptz not null default now()
);
create index idx_ai_interaction_tenant_created on ai_interaction_log(tenant_id, created_at desc);
