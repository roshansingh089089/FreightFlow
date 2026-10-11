alter table enquiries add column service_scope jsonb;
alter table enquiries add column quote_currency varchar(3);
alter table enquiries add column reference_destination_charges boolean not null default false;
