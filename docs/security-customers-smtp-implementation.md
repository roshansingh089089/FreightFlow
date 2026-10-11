# Security, customers, and SMTP implementation

This release extends the existing Spring Boot and React monolith. Incoming Gmail and IMAP sync remain separate from application-composed SMTP mail. `/api/mail/messages` still lists only imported messages for the active mailbox. `/api/mail/stored` searches messages composed in FreightFlow.

## Feature ownership

- `SecurityConfig`, `PermissionPolicy`, and `AuthController`: JWT cookie authentication, CSRF, and action permissions.
- `BusinessController`, `CustomerProfileService`, and `TenantUserController`: customer records, contacts, addresses, CC lists, ownership, and activity.
- `AiController` and `AiShipmentService`: reviewed customer extraction with field provenance.
- `OutboundMailService`, `MailAttachmentService`, `OutboundSmtpTransport`, and `OutboundMailDeliveryWorker`: drafts, attachments, recipient resolution, queueing, and SMTP submission.
- `CustomerConversationService`: grouping of linked imported mail and stored outgoing mail by RFC headers.
- `QuotationLifecycleService` and `QuoteCommunicationController`: explicit quote finalization and persistent send claims.
- React customer profile panels, mail composer, drafts, and stored mail pages consume these APIs through feature clients.

## Migrations and compatibility

- V8 adds customer credit and archive fields, contact role and auto CC, extra CC addresses, mail links, and audit events.
- V9 adds relational outbound messages, recipients, and attachment metadata with status and idempotency indexes.
- V10 adds durable quotation send claims.
- V11 adds RFC message header columns to imported mail; existing imported messages remain intact and have null values until new sync data provides headers.
- V12 adds a single-active-mailbox unique index per tenant and user. It stops with a clear error if existing records have more than one active connection; resolve those records manually before applying it. The migration does not silently deactivate an account.

Migrations are additive and were run only on a disposable PostgreSQL test container. Production data has not been inspected. Check for duplicate active mailboxes before applying V12. Company-name duplicate detection includes archived records and serializes writes through a tenant/name advisory transaction lock; a future database unique index requires a production duplicate audit first.

## API additions

| API | Purpose |
| --- | --- |
| `GET /api/auth/csrf` | Supply a token for cookie-authenticated writes. |
| `GET /api/tenant/users` | List active account-owner choices in the authenticated tenant. |
| `GET /api/customers?mine=true&sort=companyName&direction=asc` | Filter and sort tenant customers. |
| `POST /api/customers/{id}/archive`, `/restore` | Archive and restore a customer with audit events. |
| `POST /api/customers/{id}/contacts`, `PUT /contacts/{contactId}` | Manage customer contacts and auto CC. |
| `POST /api/customers/{id}/addresses`, `PUT /addresses/{addressId}` | Manage customer addresses. |
| `GET, PUT /api/customers/{id}/cc` | Read and replace additional CC recipients. |
| `GET /api/customers/{id}/activity` | Read customer activity. |
| `GET /api/customers/{id}/emails`, `POST, DELETE /emails/{messageId}` | Read and manage imported-mail links. |
| `GET /api/customers/{id}/sent-mail`, `/conversations` | Read stored outgoing mail and grouped linked messages. |
| `POST /api/ai/customers/extract` | Extract reviewed customer fields and provenance. |
| `GET, POST /api/mail/drafts`, `GET, PUT, DELETE /api/mail/drafts/{id}` | Manage owned drafts. |
| `POST /api/mail/drafts/{id}/send` | Queue a draft with an idempotency key. |
| `GET /api/mail/stored` | Search tenant-visible stored mail by customer, quotation, sender, recipient, subject, date, and status. |
| `POST /api/mail/attachments`, `GET /api/mail/attachments/{id}` | Upload and download authorized attachments. |
| `POST /api/quotations/{id}/finalize` | Explicitly finalize a validated draft. |

Existing `/api/mail/messages` and `/api/mail/sync` retain their active-connection behavior. Existing Gmail and IMAP connections continue to be supported. Outgoing mail uses the active IMAP/SMTP connection and does not claim provider mailbox synchronization.

## Security and send behavior

The server derives tenant identity from the authenticated cookie and applies tenant predicates to customer, mail, attachment, and quotation operations. `@PreAuthorize` checks action permissions. Drafts remain private to their creator; other tenant users can see only sent mail in shared customer history/search. CSRF is enforced for state-changing requests. SMTP and IMAP passwords and newly issued Gmail tokens use AES-GCM; a startup upgrade encrypts legacy Gmail plaintext tokens when the key is configured.

The delivery worker claims a queued row before sending, makes the SMTP call outside a database transaction, then records `SENT` or `NEEDS_REVIEW`. A timed-out `SENDING` row is moved to `NEEDS_REVIEW` and is not automatically resent because SMTP acceptance may be ambiguous. `SENT` means the SMTP server accepted submission; it does not prove inbox delivery. Linked quotations must be `READY_TO_SEND` before queueing. The older quotation email route also requires explicit finalization and a persistent send claim.

## RLS and database tenant constraints

PostgreSQL row-level security is deferred because the current JPA connection pool does not set and reliably clear a transaction-local tenant context on every connection. Enabling a policy without that connection lifecycle would risk denied requests or tenant leakage. Current protection is in application queries and authorization checks. Before enabling RLS, add a transaction interceptor that sets `SET LOCAL app.tenant_id` on the same connection used by JPA, resets it at transaction end, tests pooled connection reuse, and audits background workers. Composite tenant-aware foreign keys should then be added after checking existing cross-tenant references and creating unique `(tenant_id, id)` parent keys.

## Deployment blockers and limits

- Configure `MAIL_CREDENTIAL_ENCRYPTION_KEY` as a securely managed Base64-encoded 32-byte key. Do not rotate it without re-encrypting stored secrets.
- Attachment storage currently has a `local` profile implementation only. Production uploads return 503 until a private object-storage adapter and malware scan or quarantine release process are configured. Do not enable untrusted production attachments before that work.
- SMTP delivery needs a real provider acceptance test with controlled credentials, including ambiguous failure handling and Reply-To verification. No live SMTP send was performed in the test environment.
- Historical imported messages lack RFC headers until re-imported or backfilled; their conversation grouping falls back to provider thread ID or a local message key. Grouping is limited to linked and stored messages, not a complete external mailbox.
- Existing production duplicate company names, duplicate active mailboxes, and cross-tenant foreign-key references require a pre-deployment data audit. The migrations do not merge or delete data.
- Full Outlook/Gmail mailbox integration, Rate Desk, FX, and the complete quotation builder remain deferred.
