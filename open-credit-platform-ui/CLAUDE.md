# CLAUDE.md — open-credit-platform-ui

Guidance for working in this React front end. The backend rules live in the repo-root
[`CLAUDE.md`](../CLAUDE.md); this file covers only the UI. Run everything from this folder.

## Stack

React 19, TypeScript (strict, `erasableSyntaxOnly`, `verbatimModuleSyntax`), Vite, Vitest (node
environment, `src/**/*.test.ts` only), Playwright (`e2e/`), ESLint. No router library (hash routes
parsed by hand), no state library, no CSS framework or CSS Modules, no path aliases — imports are
relative.

```
npm run dev        # Vite dev server (API at VITE_API_URL, default http://localhost:9000)
npm run build      # tsc -b && vite build
npm run lint
npm test           # vitest
npm run test:e2e   # playwright
```

Before reporting a change done, run `npx tsc -b`, `npm run lint` and `npm test`. Copy `.env.example`
to `.env` to override `VITE_API_URL` / `VITE_DOCS_URL`.

## Folder structure

Feature-first. A feature owns everything for one business area; shared code lives in the global
folders only when two or more features use it.

```
src/
├── main.tsx, App.tsx       # entry (imports global CSS) + app root
├── routes/                 # AppRoutes (hash-route switch), ConsoleGate (sign-in gate / protected route)
├── pages/                  # LandingPage/, ConsolePage/ (+ ConsoleSidebar, route.ts). Compose features.
├── features/               # One folder per domain area (see below)
│   └── <feature>/
│       ├── index.ts        # Public API — the ONLY thing other features/pages may import
│       ├── components/     # Components used only by this feature (default exports)
│       ├── services/       # <feature>Service.ts: requests + response mapping (+ colocated tests)
│       ├── types.ts        # Types for this feature's data
│       └── *.ts            # Feature-local logic/constants (e.g. loans/emi.ts, loans/stages.ts)
├── components/
│   ├── common/             # Domain-agnostic primitives, one folder each: Badge, Card, Facts, Field, Icon
│   └── layout/             # Header, Footer
├── context/                # SessionContext
├── hooks/                  # useFetch, useRoles (shared hooks)
├── services/               # http.ts: call, ApiFailure, token handling
├── config/                 # env.ts: API, DOCS_URL, REPO (the only reader of import.meta.env)
├── types/                  # Shared types: Session, API Envelope
├── utils/                  # Pure helpers (format.ts: money, dt, msg)
└── styles/                 # Global CSS: index.css (tokens/reset), landing.css, console.css
```

Current features: `auth` (login), `loans` (stages, new application, loan detail, KYC), `dashboard`
(stats), `assessment` (financial statements, score, decision), `approvals` (maker-checker queue and
panel), `audit` (timeline), `landing` (marketing page content, lifecycle, code preview).

Intentionally absent from the usual React template: `store/` (no state library — don't add one),
top-level `tests/` (tests are colocated; browser flows live in `e2e/`), `layouts/` (use
`components/layout/`), `assets/` (add `assets/images|fonts|icons` with the first real asset).

### Rules

1. **Import across features only through `index.ts`** (`from '../../loans'`), never into another
   feature's `components/` or `services/`. Inside a feature, import files directly. Exception:
   `landing/content.ts` is imported directly by `LandingPage`, since it is data, not a feature API.
2. **No cycles between features.** Current direction: `loans` -> `assessment`, `approvals`, `audit`;
   `approvals`/`audit` -> `assessment`; `dashboard` -> `loans`. If two features need each other,
   move the shared piece into a global folder or merge them.
3. **Features never import from `pages/` or `routes/`.** Pages and routes import from features and
   global folders; routes may also import pages.
4. **Global folders never import from `features/`:** `components/`, `context/`, `hooks/`,
   `services/`, `config/`, `types/`, `utils/` stay domain-agnostic. If something knows about loans or
   approvals, it belongs in a feature (or, if it is page chrome, the page — see `ConsoleSidebar`).
5. **Barrels export the public surface only** — what other features/pages need — not every file.
6. **Placement test for new code:** used by one feature -> that feature; used by several and
   domain-agnostic -> global folder; a new route -> `pages/` + `routes/` entry, with its logic in a
   feature. Don't create a global file for a single consumer or a feature folder for a single
   component. Create a feature `hooks/` or `services/` folder only when it has content.
7. **Components stay presentational.** HTTP goes through `services/http.ts`; endpoint-specific
   requests and response shaping go in the feature's `services/` (see `auth/services/authService.ts`,
   `loans/services/loanService.ts`). Existing inline `call(...)` in action handlers is tolerated;
   extract when touching it.
8. **Types live next to the data they describe** (`types.ts`, or global `types/` when shared by
   several features); types exported from a barrel use `export type`.
9. **Tests are colocated** with the code (`foo.ts` + `foo.test.ts`). Financial math (e.g.
   `loans/emi.ts`) and API mapping always get tests; Vitest runs in `node`, so test logic, not DOM.
10. **Env access only via `config/env.ts`.** No `import.meta.env` elsewhere.

## Conventions

- Money: values come from the API as numbers for display only; format with `utils/format.ts`
  (`money`, `dt`). Do not do currency arithmetic in the UI beyond the indicative EMI preview; the
  server is the source of truth for amounts, rates and decisions.
- Role gating (`hooks/useRoles`) only hides actions for UX. The backend enforces authorization;
  never treat a hidden button as security.
- The bearer token lives in memory in `services/http.ts` only — never `localStorage`/cookies. A 401
  while signed in triggers the unauthorized handler (`ConsoleGate` returns to sign-in).
- Console routes are hash-based (`#/console/...`), parsed in `pages/ConsolePage/route.ts`. Add a
  route by extending `View`/`parseConsoleRoute` there and rendering it in `ConsolePage.tsx`.
- Styling is plain global CSS with class names. All three stylesheets are imported once, in order,
  in `main.tsx` (console rules override landing ones, so keep that order).
- Landing page copy and code snippets live in `features/landing/content.ts`, not in JSX.
- Don't add libraries (router, state, UI kit, alias plugins) without asking; the codebase
  deliberately stays dependency-light.
