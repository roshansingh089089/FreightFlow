alter table mail_threads add column resolution_status varchar(24) not null default 'NORMAL';
alter table mail_threads add constraint ck_mail_thread_resolution_status
 check (resolution_status in ('NORMAL','AMBIGUOUS_REFERENCE','AMBIGUOUS_CUSTOMER'));
