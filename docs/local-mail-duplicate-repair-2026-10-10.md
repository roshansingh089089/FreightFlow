# Local Hostinger Inbox duplicate repair (2026-10-10)

This report applies only to the Docker PostgreSQL database reached through
`localhost:5433/freightflow`. The full custom-format backup is
`/tmp/freightflow-local-mail-backup-20261010-221708.dump` (149,384 bytes).
No production or remote database was accessed.

The live IMAP `INBOX` contained five UIDs (`1`–`5`) at UIDVALIDITY
`1791517077`; three were unread (`1`, `3`, `5`). Before repair, the active
connection `72b993b0-d56f-4fa2-b565-d360e5a71604` had eight rows: five
UID-backed rows and three legacy `mid:` rows. The exact SHA-256 hash of each
canonical RFC Message-ID matched the respective legacy provider ID.

| UID | Canonical row | Removed legacy row | Dependent records |
| --- | --- | --- | --- |
| 1 | `11ef784e-1ed7-438d-b95a-466b2af25747` | `842e9a20-1a59-47f6-84cf-84ca96a60a6a` | None |
| 2 | `1f1e047b-82aa-49a9-b1c4-e3e49e88a99c` | `6d537eba-1db1-4ebf-b639-37ee43df6de2` | One customer link moved |
| 3 | `453072b4-8f75-4977-8f08-d938d912c91f` | `d972d25e-5ea2-48dd-af68-17acf62743b4` | One customer link moved |

Foreign keys to inbound messages were inspected in `customer_mail_links`,
`inbound_mail_attachments`, `outbound_messages`, and the later `enquiries`
schema. No attachment, outbound reply, quotation, or send-attempt record
referenced the removed rows. The two customer links were moved to their
canonical rows. Three now-empty legacy threads were removed; canonical
threads and message headers remained intact.

The repair script was first executed with `ROLLBACK`, then committed on the
local database. It verifies exact tenant, mailbox, UIDVALIDITY, UID, legacy
provider hash, sender, subject, and row IDs before changes. Flyway versions
23–26 were then applied locally. After a new-code sync, the database had
five current Inbox rows and three unread, matching IMAP. The folder-scoped
unique index rejected a duplicate-UID insert in a rolled-back verification
transaction.
