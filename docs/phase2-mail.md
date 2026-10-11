# Phase 2 mail module

FreightFlow uses the existing authenticated user's active `mail_connections` row. IMAP/SMTP
credentials remain encrypted with `MAIL_CREDENTIAL_ENCRYPTION_KEY`. Mail reads, threads,
drafts, and downloads are scoped to the tenant and the active mailbox. The older
`/api/mail/messages` response remains compatible; new search and thread routes are additive.

## Configuration

- `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`: PostgreSQL connection.
- `JWT_SECRET`: existing session signing secret.
- `MAIL_CREDENTIAL_ENCRYPTION_KEY`: existing 32-byte Base64 encryption key.
- `MAIL_ATTACHMENTS_PRIVATE_DIR`: private filesystem directory for attachments in non-local
  profiles. Each application replica must use the same private volume. The `local` profile
  uses `mail.attachments.local-dir` (default `/tmp/freightflow-mail-dev`).
- `mail.sync.poll-ms`: IMAP polling interval, default 60000 ms.
- `mail.sync.accounts-per-poll`: maximum accounts per poll, default 10.
- `mail.outbox.poll-ms`: queued SMTP submission interval, default 5000 ms.
- `mail.attachments.cleanup-ms`: abandoned upload cleanup interval, default 3600000 ms.

Each mailbox is configured in Settings. No SMTP or IMAP password is returned by the
connection API. SMTP `SENT` means submission server acceptance. It does not confirm delivery
to the recipient. `NEEDS_REVIEW` and `UNKNOWN` attempts require manual reconciliation; the
worker does not retry an ambiguous result.

## Storage and migration

V13 adds internal threads, reply mode/source, IMAP UID and UIDVALIDITY cursors, and a sync
lease. V14 records SMTP attempts. V15 stores metadata for supported incoming attachments.
V16 distinguishes a quotation's primary send from later follow-up mail. V17 adds a lease
owner for renewal. V18 joins historical messages only through unambiguous RFC parent IDs;
it never groups by subject. V19 marks ambiguous reference/customer resolution for review.
V20 adds tenant and mailbox reference guards, and V21 indexes mailbox search and polling.
V22 records employee reconciliation decisions for uncertain SMTP outcomes.
All migrations are additive after V12. Existing mailbox and outbound records are retained.

Uploaded and imported files are stored privately under UUID keys. The API checks the active
mailbox before an incoming attachment download. Supported import and upload signatures are
PDF, PNG, JPEG, and UTF-8 plain text, with a 10 MB limit. Imported unsupported MIME parts
are skipped. Unattached outbound uploads older than one day are removed.

## Routes added

- `GET /api/mail/threads`, `GET /api/mail/threads/{id}`,
  `GET /api/mail/threads/{id}/messages`
- `POST /api/mail/threads/{id}/customer`, `DELETE /api/mail/threads/{id}/customer`
- `POST /api/mail/drafts/reply/{messageId}`,
  `POST /api/mail/drafts/reply-all/{messageId}`
- `POST /api/mail/drafts/{id}/reconcile` with a confirmed outcome and note
- `GET /api/mail/search` with pagination and sender, recipient, subject, direction,
  status, customer, quotation, date, and read filters
- `GET /api/mail/messages/{messageId}/attachments`,
  `GET /api/mail/messages/{messageId}/attachments/{id}`
- `DELETE /api/mail/attachments/{id}` for unattached outbound uploads
- `GET /api/customers/{id}/mail-threads`
- `POST /api/quotations/{id}/mail-draft` with optional `replyToMessageId`
- `POST /api/ai/mail/draft` for editable suggestions

The Mail composer offers draft, reply, and tone suggestions. The reading pane offers a
customer email summary. AI output is shown for employee review and is never sent automatically.

Existing draft, stored-mail, connection, manual-sync, customer sent-mail, quotation preview,
and quotation send routes remain available.

## Protocol notes

IMAP polls bounded UID windows and records UIDVALIDITY plus UID to prevent duplicate imports.
It imports RFC Message-ID, In-Reply-To, References, Reply-To, To, CC, body text, read state,
and supported attachments. Inbox polling uses a database lease renewed while syncing.
SMTP outbox work is claimed atomically and stores a send attempt. New mail has no reply
headers. Reply and Reply all derive headers from the selected stored message. Reply all
deduplicates recipients and excludes the configured sending identity. The message ID uses
the configured mailbox domain. After SMTP acceptance, IMAP APPEND to Sent is attempted
without triggering another SMTP send; local sent history remains authoritative if APPEND
is unsupported.

## Known limits

- SMTP acceptance does not prove final delivery. The application does not parse delivery
  status notifications or reconcile provider-side sent copies automatically.
- Provider folder synchronization remains limited to INBOX. Sent history is maintained
  locally and Sent APPEND is best effort.
- Embedded GreenMail tests verify IMAP header import and SMTP reply MIME headers,
  recipients, and PDF attachment submission. PostgreSQL integration tests verify
  migrations, persistence, HTTP security, and search. A single end-to-end test that
  combines PostgreSQL, the background workers, and a real mailbox configuration is
  still required before claiming provider-specific delivery behavior. The embedded
  protocol test uses localhost without TLS; production TLS negotiation is not exercised.
- Imported attachments outside the approved file types or size limit are skipped.
- Mail search and customer thread history operate on the active mailbox; older inactive
  mailboxes remain in the database but are not shown in the current mailbox workspace.
