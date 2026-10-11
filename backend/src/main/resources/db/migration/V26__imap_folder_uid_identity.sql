-- UID values are scoped to a mailbox folder and its UIDVALIDITY epoch.
alter table email_messages add column imap_folder varchar(255) not null default 'INBOX';

create unique index uq_email_imap_folder_uid
 on email_messages(connection_id,imap_folder,imap_uid_validity,imap_uid)
 where imap_uid is not null and imap_uid_validity is not null;

drop index uq_email_imap_uid;
