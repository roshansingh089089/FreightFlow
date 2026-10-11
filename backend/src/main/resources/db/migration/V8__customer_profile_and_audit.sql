alter table customers add column if not exists credit_terms_days integer;
alter table customers add column if not exists credit_limit numeric(18,4);
alter table customers add column if not exists archived_at timestamptz;
alter table customer_contacts add column if not exists contact_role varchar(40);
alter table customer_contacts add column if not exists auto_cc boolean not null default false;
alter table customer_contacts add column if not exists active boolean not null default true;

create table customer_extra_cc (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 customer_id uuid not null references customers(id),
 email varchar(255) not null,
 display_name varchar(150),
 active boolean not null default true,
 created_at timestamptz not null,
 updated_at timestamptz not null
);
create unique index uq_customer_extra_cc_active_email on customer_extra_cc(tenant_id,customer_id,lower(email)) where active;
create index idx_customer_extra_cc_tenant_customer on customer_extra_cc(tenant_id,customer_id);
create index idx_customer_contacts_tenant_email on customer_contacts(tenant_id,lower(email));

create table customer_mail_links (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 customer_id uuid not null references customers(id),
 message_id uuid not null references email_messages(id),
 linked_by uuid not null references users(id),
 linked_at timestamptz not null,
 unique(tenant_id,message_id)
);
create index idx_customer_mail_links_customer on customer_mail_links(tenant_id,customer_id,linked_at desc);

create table business_audit_events (
 id uuid primary key,
 tenant_id uuid not null references tenants(id),
 actor_user_id uuid not null references users(id),
 entity_type varchar(50) not null,
 entity_id uuid not null,
 action varchar(60) not null,
 detail text,
 created_at timestamptz not null default now()
);
create index idx_business_audit_entity on business_audit_events(tenant_id,entity_type,entity_id,created_at desc);
