create sequence customer_code_seq start 1;
create sequence enquiry_code_seq start 1;
create sequence quotation_code_seq start 1;
select setval('customer_code_seq', coalesce((select max(substring(customer_code from '[0-9]+$')::bigint) from customers), 1), exists(select 1 from customers));
select setval('enquiry_code_seq', coalesce((select max(substring(enquiry_code from '[0-9]+$')::bigint) from enquiries), 1), exists(select 1 from enquiries));
select setval('quotation_code_seq', coalesce((select max(substring(quotation_number from '[0-9]+$')::bigint) from quotations), 1), exists(select 1 from quotations));
