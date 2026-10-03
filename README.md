# FreightFlow MVP

A modular monolith freight forwarding workspace. Employees can register a company, create customers and enquiries, prepare manual quotations, preview a PDF, edit a prepared email, and explicitly send it with an attached PDF.

## Prerequisites

- Java 21 and Maven 3.9+
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
mvn spring-boot:run
```

Start the frontend in another terminal:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and create a company account. The first user is its ADMIN. PostgreSQL is on host port 5433, the backend on 8080, and Vite on 5173. Flyway creates the schema on backend startup. No sample credentials are created, so production cannot accidentally use a seeded account.

## Email setup

Configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_AUTH`, and `SMTP_TLS_ENABLED` in `.env`. For Gmail, use `smtp.gmail.com`, port `587`, TLS and TLS required `true`, auth `true`, your full Gmail address for both `SMTP_USERNAME` and `SMTP_FROM`, and a Google app password for `SMTP_PASSWORD` (not your normal Google password). Enable 2-Step Verification in your Google Account to create an app password. After editing `.env`, restart the backend; the running process does not reload environment variables. If `SMTP_PASSWORD` is blank, the send endpoint reports `SMTP_NOT_CONFIGURED` and leaves the quotation as a draft. A generic SMTP provider can use its own host, port, and credentials. Keep `.env` private. Creating or editing a quotation never sends mail. The employee must open **Email Quote**, review the fields, then click **Send Email**. Failed attempts are recorded without marking the quotation SENT.

For HTTPS deployments set `COOKIE_SECURE=true`, set `FRONTEND_ORIGIN` to the exact frontend origin, and supply a long random `JWT_SECRET`. The auth token is stored only in an HttpOnly SameSite cookie.

## AI assistance

Set `GEMINI_API_KEY` in your private `.env` and restart the backend. `GEMINI_MODEL` defaults to `gemini-3.5-flash-lite`. The key is used only by Spring Boot; the browser never receives it. Without a key, AI actions return `AI_NOT_CONFIGURED` and all manual workflows remain available.

On **Create Enquiry**, choose **AI Shipment Intake**, paste any shipment note, review the structured result, resolve conflicts with existing fields, and click **Apply to Enquiry**. Applying values does not save the enquiry. Enquiry details can draft a missing-information message; quotation editing can produce wording, terms, email drafts, and inconsistency suggestions. Employees review and manually copy or use drafts; AI never sends email or changes financial values. AI usage logs contain action metadata only.

The backend uses Gemini structured JSON output through the official REST `generateContent` endpoint. No live Gemini requests run during automated tests.

## Checks

```bash
cd backend && mvn test
cd frontend && npm run build
```

## Structure

- `backend`: Spring Boot REST API, JPA entities, Flyway schema, PDF and SMTP services
- `frontend`: React, Vite, TypeScript, TanStack Query, React Hook Form, Zod, Tailwind
- `docker-compose.yml`: PostgreSQL only

Business APIs resolve the tenant from the authenticated user and look up ID-based records within that tenant. PDFs are generated on demand. Future modules in the sidebar are intentionally disabled.
