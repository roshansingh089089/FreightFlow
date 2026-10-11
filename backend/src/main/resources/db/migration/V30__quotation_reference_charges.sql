-- Reference charges remain visible on a quotation but never contribute to its total.
alter table quotation_lines add column pricing_treatment varchar(20) not null default 'INCLUDED';
alter table quotation_lines add constraint quotation_lines_pricing_treatment_check
    check (pricing_treatment in ('INCLUDED', 'FOR_REFERENCE'));
