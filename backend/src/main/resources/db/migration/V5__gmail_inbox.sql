create table gmail_oauth_states (
 id uuid primary key, tenant_id uuid not null references tenants(id), user_id uuid not null references users(id),
 state_hash varchar(64) not null unique, expires_at timestamptz not null, created_at timestamptz not null
);
create table gmail_connections (
 id uuid primary key, tenant_id uuid not null references tenants(id), user_id uuid not null references users(id),
 provider varchar(20) not null, email_address varchar(255) not null,
 access_token text not null, refresh_token text not null, token_expiry timestamptz not null,
 status varchar(20) not null, last_synced_at timestamptz, created_at timestamptz not null, updated_at timestamptz not null,
 unique(tenant_id,user_id,provider)
);
create table email_messages (
 id uuid primary key, tenant_id uuid not null references tenants(id), connection_id uuid not null references gmail_connections(id),
 provider_message_id varchar(255) not null, provider_thread_id varchar(255),
 from_name varchar(255), from_email varchar(255), to_email text, subject text, body_text text,
 received_at timestamptz not null, is_read boolean not null, created_at timestamptz not null,
 unique(connection_id,provider_message_id)
);
create index idx_email_messages_tenant_received on email_messages(tenant_id,received_at desc);
