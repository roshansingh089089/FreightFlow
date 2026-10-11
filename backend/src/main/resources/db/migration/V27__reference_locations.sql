create extension if not exists pg_trgm;

create table reference_locations (
  code varchar(5) primary key,
  country_code varchar(2) not null,
  name varchar(255) not null,
  aliases text not null default '',
  functions varchar(12) not null default '',
  source_version varchar(20) not null,
  updated_at timestamptz not null default now(),
  constraint reference_locations_code_format check (code ~ '^[A-Z]{2}[A-Z0-9]{3}$')
);

create index reference_locations_name_trgm on reference_locations using gin (lower(name) gin_trgm_ops);
create index reference_locations_alias_trgm on reference_locations using gin (lower(aliases) gin_trgm_ops);
create index reference_locations_country_code on reference_locations(country_code, code);

alter table enquiries add column pol_code varchar(5) references reference_locations(code);
alter table enquiries add column pod_code varchar(5) references reference_locations(code);
alter table enquiries add column fpod_code varchar(5) references reference_locations(code);

create index enquiries_pol_code on enquiries(pol_code) where pol_code is not null;
create index enquiries_pod_code on enquiries(pod_code) where pod_code is not null;
