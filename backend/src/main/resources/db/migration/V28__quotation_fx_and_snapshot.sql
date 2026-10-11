create table fx_reference_rates (
  currency varchar(3) not null,
  observation_date date not null,
  units_per_eur numeric(22,10) not null check (units_per_eur > 0),
  source varchar(30) not null,
  fetched_at timestamptz not null,
  primary key (currency, observation_date)
);
create index fx_reference_rates_latest on fx_reference_rates(currency, observation_date desc);

create table fx_manual_rates (
  id uuid primary key,
  tenant_id uuid not null references tenants(id),
  from_currency varchar(3) not null,
  to_currency varchar(3) not null,
  rate numeric(22,10) not null check (rate > 0),
  effective_date date not null,
  reason text not null,
  created_by uuid not null references users(id),
  created_at timestamptz not null default now()
);
create index fx_manual_rates_lookup on fx_manual_rates(tenant_id, from_currency, to_currency, effective_date desc);

alter table quotation_lines add column original_currency varchar(3);
alter table quotation_lines add column original_unit_price numeric(14,2);
alter table quotation_lines add column fx_rate numeric(22,10);
alter table quotation_lines add column fx_reference_rate numeric(22,10);
alter table quotation_lines add column fx_source varchar(30);
alter table quotation_lines add column fx_observation_date date;
alter table quotation_lines add column fx_buffer_percent numeric(8,4);

alter table quotations add column shipment_snapshot text;
alter table quotations add column source_message_id uuid;
alter table quotations add column source_thread_id uuid;
alter table quotations add column source_connection_id uuid;
alter table quotations add column fx_frozen_at timestamptz;
