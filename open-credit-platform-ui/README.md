# Open Credit Platform UI

React + TypeScript + Vite front end for the [Open Credit Platform](../README.md) API. It has two parts:

- **Landing page** (`/`): product overview, integration code preview (cURL, JavaScript, Java, Python) with a live
  "Run request" button, the eleven-stage loan lifecycle, security controls, and developer documentation links.
- **Loan console** (`/#/console`): sign in, then work applications through the whole lifecycle.

## Loan console

| Area | What it does |
|------|--------------|
| Dashboard | KPIs (totals, pipeline, approval rate, sanctioned, disbursed, declined), pipeline by stage, requested value by stage, applications per day (last 14 UTC days), product mix, referred applications, recent applications. All figures come from `GET /api/loans/stats`. |
| New application | Registers the customer and applies for a personal or vehicle loan, with an indicative EMI preview. |
| Search | Finds applications in any stage by reference or applicant name (server-side). |
| Approvals | The maker–checker queue by status (waiting for maker, waiting for checker, approved, rejected), with a pending count in the sidebar. |
| Stages | One entry per stage (Draft, Submitted, Underwriting, Offered, Sanctioned, Disbursed, Declined), each listing its applications with a server-side filter and paging. |
| Application | The data for every stage on one page, with the actions available at its current stage: KYC and documents, underwriting, financial statements, analysis, score, credit decision, the approval case, offers, sanction, disbursement tranches, and an audit trail. |

Console views have their own URLs, for example `#/console/stage/OFFERED`, `#/console/approvals` and
`#/console/loan/LN-00001030`, so back and forward work.

**Roles.** The UI hides actions the signed-in role would be refused, mirroring the backend's `@PreAuthorize` rules:
makers create, submit, prepare (KYC start, financial statements, scoring, decision, offers) and open the approval
case and recommend; checkers verify KYC and documents, sanction, disburse, record the final approval and read the audit
trail; ADMIN can do both. The checker of a case must be a different user than its maker, so use two logins to approve.
The server is always the authority.

**Sessions.** The bearer token lives in memory only. A refresh, or a 401 from the API, returns to the sign-in screen
with a "session expired" notice.

**Documents.** The API records a document's type, file name and storage reference only, not file contents, and the
form says so.

## Run it

The UI needs the backend and PostgreSQL running.

```bash
# 1. PostgreSQL (from Open-Credit-Platform/)
docker compose up postgres -d        # or any local Postgres, see the backend README for DB_URL/DB_USER/DB_PASSWORD

# 2. Backend, dev profile (from Open-Credit-Platform/)
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run

# 3. UI (from open-credit-platform-ui/)
npm install
npm run dev
```

| What | URL |
|------|-----|
| UI | http://localhost:5173 |
| Loan console | http://localhost:5173/#/console |
| Backend | http://localhost:9000 |
| Swagger UI | http://localhost:9000/swagger-ui.html |

The `dev` profile is required: without it the backend fails to boot unless `JWT_SECRET` is set, and the demo
logins are deactivated.

## Demo logins

Seeded by the backend (dev profile only). Rotate or remove them before any real deployment.

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | ADMIN |
| `alice.maker` | `maker123` | MAKER |
| `bob.checker` | `checker123` | CHECKER (credit officer) |
| `carol.checker` | `checker123` | CHECKER (senior credit manager) |

The landing page's "Run request" button signs in as `alice.maker`.

## Configuration

Set in `.env.local` or the environment (Vite only exposes variables prefixed `VITE_`):

| Variable | Default | Purpose |
|----------|---------|---------|
| `VITE_API_URL` | `http://localhost:9000` | Backend base URL |
| `VITE_DOCS_URL` | `<VITE_API_URL>/swagger-ui.html` | Target of the "API reference" links |
| `UI_URL` | `http://localhost:5173` | Base URL for the Playwright tests |

The backend allows browser calls only from origins in `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`).
If you serve the UI from another origin or port, set that variable on the backend too.

## Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Dev server with hot reload |
| `npm run build` | Type-check and production build into `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | ESLint |
| `npm test` | Vitest unit tests (API wrapper, stats mapping, EMI maths) |
| `npm run test:e2e` | Playwright end-to-end test of the maker–checker flow |

The end-to-end test needs the full stack running (PostgreSQL, the backend with the `dev` profile, and `npm run dev`)
and uses the locally installed Chrome, so no browser download is needed. It creates a new application each run.

## Backend endpoints added for the console

- `GET /api/approval-cases?status=&page=&size=`: the approval queue, oldest first, each row with its application.
- `GET /api/loans?q=`: case-insensitive search by reference number or applicant name, combinable with `status`.
- `GET /api/loans/stats`: portfolio totals computed in the database.

All three are covered by integration tests and listed in [`docs/api/api-overview.md`](../docs/api/api-overview.md).

## Project layout

```
src/
  main.tsx          entry point
  App.tsx           landing page, header and hash routing (#/console)
  App.css           landing page styles
  Console.tsx       login, console shell with sidebar navigation, new application form
  Dashboard.tsx     KPIs and charts
  StageList.tsx     paged list for one stage, or the cross-stage search
  Approvals.tsx     maker–checker queue
  LoanDetail.tsx    per-application page and its actions
  KycPanel.tsx      KYC and documents
  Assessment.tsx    financial statements, analysis, score and decision
  assessmentData.ts types and loader for the assessment chain
  ApprovalPanel.tsx approval case and maker/checker decisions
  AuditTimeline.tsx merged business events and data changes
  session.ts        session context and role helpers
  ui.tsx            small shared components (Badge, Card, Field)
  useFetch.ts       data-loading hook
  emi.ts            indicative EMI estimate
  api.ts            fetch wrapper, types, stage definitions and stats loader
  *.test.ts         unit tests
  *.css             styles (design tokens in index.css)
e2e/                Playwright tests
```

## Design notes

- Minimal Swiss-style layout, navy and slate palette with a teal accent, Inter and JetBrains Mono.
- No UI framework or router dependency; routing is a single hash check.
- The EMI preview mirrors the backend's reducing-balance formula and its hard-coded rates; the backend prices the loan
  on submission. The unit tests pin the formula to the backend's own test values.
- Responses use the backend's `ApiResponse` envelope; validation errors are shown per field and other errors by
  message.
- The landing page lists SOC 2, ISO 27001, PCI DSS and GDPR as "Not certified": the project has not been
  independently audited. Update that section if real certifications are obtained.
