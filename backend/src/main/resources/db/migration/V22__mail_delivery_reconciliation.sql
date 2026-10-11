alter table outbound_messages add column reconciled_by uuid references users(id);
alter table outbound_messages add column reconciled_at timestamptz;
alter table outbound_messages add column reconciliation_note text;
