# FreightFlow MVP

A modular monolith freight forwarding workspace. Employees can register a company, create customers and enquiries, prepare manual quotations, preview a PDF, edit a prepared email, and explicitly send it with an attached PDF.

## Prerequisites

- Java 21 (the backend includes a Gradle wrapper; no system Gradle installation is needed)
- Node 20+ and npm
- Docker with the daemon running

## Start locally

From the repository root:

```bash
cp .env.example .env
# Set JWT_SECRET to a random value of at least 32 characters.
docker compose up -d
```

Start the backend in another terminal:

```bash
cd backend
set -a; source ../.env; set +a
./gradlew bootRun
```

Start the frontend in another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and create a company account. The first user is its ADMIN. PostgreSQL is on host port 5433, the backend on 8080, and Vite on 5173. Flyway creates the schema on backend startup. No sample credentials are created, so production cannot accidentally use a seeded account.

## Email setup

Hostinger Business Email is the default mailbox option in Settings. Its IMAP and SMTP credentials are entered per user, encrypted in the database, and selected from that user's active connection. App-wide `SMTP_*` settings and Google OAuth credentials are optional legacy configuration; neither is required for startup. Without an active connection, quotation sending reports `Mail account not configured`. Employees still review **Email Quote** and explicitly send it. Failed attempts are logged without marking the quotation SENT.

For HTTPS deployments set `COOKIE_SECURE=true`, set `FRONTEND_ORIGIN` to the exact frontend origin, and supply a long random `JWT_SECRET`. The auth token is stored only in an HttpOnly SameSite cookie.

## AI assistance

Set `GEMINI_API_KEY` in your private `.env` and restart the backend. `GEMINI_MODEL` defaults to `gemini-3.5-flash-lite`. The key is used only by Spring Boot; the browser never receives it. Without a key, AI actions return `AI_NOT_CONFIGURED` and all manual workflows remain available.

On **Create Enquiry**, choose **AI Shipment Intake**, paste any shipment note, review the structured result, resolve conflicts with existing fields, and click **Apply to Enquiry**. Applying values does not save the enquiry. Enquiry details can draft a missing-information message; quotation editing can produce wording, terms, email drafts, and inconsistency suggestions. Employees review and manually copy or use drafts; AI never sends email or changes financial values. AI usage logs contain action metadata only.

The backend uses Gemini structured JSON output through the official REST `generateContent` endpoint. No live Gemini requests run during automated tests.

## Optional Gmail compatibility

Existing Gmail OAuth code and connection rows remain supported. Google client ID, secret, and redirect URI are optional and are checked only when Gmail OAuth is used. Existing Gmail OAuth tokens in `mail_connections` remain plaintext from the earlier implementation; this Hostinger setup does not migrate them. Gmail is no longer a primary Settings option.

## Hostinger Business Email: local run and manual check

1. `cd /Users/shristysingh/Documents/FrieghtFLOW`.
2. Generate a Base64 key once with `openssl rand -base64 32`; keep it private and stable across restarts.
3. Put `MAIL_CREDENTIAL_ENCRYPTION_KEY=<generated-value>` in the private `.env`. Keep the existing JWT, database, frontend origin, and optional Gemini values. Do not put the mailbox address or password in `.env`.
4. Load the private file using `set -a`, `source .env`, `set +a`.
5. Start PostgreSQL, then run `cd backend` and `./gradlew bootRun`.
6. In another terminal, load the same `.env`, run `cd frontend` and `npm run dev`.
7. Log in to FreightFlow and open **Settings**.
8. Select **Hostinger Email**.
9. Enter the actual Hostinger mailbox email address and mailbox password. Default settings are IMAP `imap.hostinger.com:993` with implicit TLS and SMTP `smtp.hostinger.com:465` with implicit TLS. Advanced settings remain editable.
10. Click **Test connection**. Check for **IMAP OK · SMTP OK**. The password fields clear after the test; reenter the password for the next step.
11. Click **Save connection**. The backend tests again before activating and encrypting the mailbox credentials.
12. Open `/customers` in Mail or Side by side mode and click **Sync mail**.
13. Confirm inbox messages appear; select one and inspect its body. Sync reads INBOX without changing flags, moving, or deleting messages.
14. Create a quotation, review its email, send it, and confirm receipt from the Hostinger mailbox.

When switching mailbox addresses, the database keeps the previous connection ID so historical messages stay with the original mailbox. Disconnect removes stored credentials but retains imported message rows. A disconnected mailbox's history is not displayed by the active-mailbox list API. The server-side encryption key is required to decrypt saved mailbox passwords. Do not rotate or lose it without a credential migration plan.

## IntelliJ IDEA

Open `backend/` as a Gradle project. Set the Project SDK and Gradle JVM to Java 21 and use the Gradle wrapper. Create a Gradle run configuration for the `bootRun` task in the `backend` project. Set its working directory to `backend/` and supply the variables from the private root `.env` as environment variables; IntelliJ does not load that file automatically. Use `SPRING_PROFILES_ACTIVE=local` and a `JWT_SECRET` of at least 32 characters. The local profile defaults to PostgreSQL at `localhost:5433`; `.env.example` lists the database, frontend, Gemini, and mailbox encryption variables.

## Checks

```bash
cd backend && ./gradlew clean build
cd backend && ./gradlew bootJar
cd frontend && npm run build
```

## Structure

- `backend`: Spring Boot REST API, JPA entities, Flyway schema, PDF and email delivery services
- `frontend`: React, Vite, TypeScript, TanStack Query, React Hook Form, Zod, Tailwind
- `docker-compose.yml`: PostgreSQL only

Business APIs resolve the tenant from the authenticated user and look up ID-based records within that tenant. PDFs are generated on demand. Future modules in the sidebar are intentionally disabled.

## Code formatting

Java source and test files use **Google Java Format 1.25.2** through Gradle Spotless.
Run `cd backend && ./gradlew spotlessApply` to format and
`./gradlew spotlessCheck` to verify. The configuration also orders imports and
removes unused imports. Use explicit type imports; static wildcard imports in
JUnit/Mockito test helpers are allowed.

Frontend TypeScript, React, and CSS use **Prettier 3.6.2** with the
`prettier-plugin-organize-imports` plugin. Run `cd frontend && npm run format`
to format and `npm run format:check` to verify. `npm run typecheck` checks
TypeScript. Flyway migrations and generated files are excluded from both
formatters.
