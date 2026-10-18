# CLAUDE.md — open-credit-platform-ui

Guidance for working in this React front end. The backend rules live in the repo-root
[`CLAUDE.md`](../CLAUDE.md); this file covers only the UI. Run everything from this folder.

## Stack

React 19, TypeScript (strict, `erasableSyntaxOnly`, `verbatimModuleSyntax`), Vite, Vitest (node
environment, `src/**/*.test.ts` only), Playwright (`e2e/`), ESLint. No router library (hash routes
parsed by hand), no state library, no CSS framework, no path aliases — imports are relative.

```
npm run dev        # Vite dev server (API at VITE_API_URL, default http://localhost:9000)
npm run build      # tsc -b && vite build
npm run lint
npm test           # vitest
npm run test:e2e   # playwright
```

Before reporting a change done, run `npx tsc -b`, `npm run lint` and `npm test`.

## Folder structure

Feature-first. A feature owns everything for one business area; shared code lives in the global
folders only when two or more features use it.

```
src/
├── main.tsx, App.tsx       # entry + app shell (header, footer, hash-route switch)
├── pages/                  # Route views. Compose features; hold no business logic of their own.
├── features/               # One folder per domain area (see below)
│   └── <feature>/
│       ├── index.ts        # Public API — the ONLY thing other features may import
│       ├── api.ts          # Requests + response mapping for this feature
│       ├── types.ts        # Types for this feature's data
│       ├── components/     # Components used only by this feature (default exports)
│       └── *.ts            # Feature-local logic/constants (e.g. loans/emi.ts, loans/stages.ts)
├── components/             # Shared UI primitives (ui.tsx: Field, Badge, Card, Facts)
├── hooks/                  # Shared hooks (useFetch)
├── services/               # Core API client (http.ts: call, ApiFailure, token handling)
├── utils/                  # Pure helpers (format.ts: money, dt, msg)
└── styles/                 # Global CSS: index.css (tokens/reset), landing.css, console.css
```

Current features: `auth` (login, session context, role flags), `loans` (stages, new application,
loan detail, KYC), `dashboard` (stats), `assessment` (financial statements, score, decision),
`approvals` (maker-checker queue and panel), `audit` (timeline), `landing` (marketing page content).

### Rules

1. **Import across features only through `index.ts`** (`from '../../auth'`), never into another
   feature's `components/` or `api.ts`. Inside a feature, import files directly.
2. **No cycles between features.** Current direction: `loans` -> `assessment`, `approvals`, `audit`,
   `auth`; `approvals`/`audit` -> `assessment`, `auth`; `dashboard` -> `loans`. If two features need
   each other, move the shared piece down into the global folders or merge them.
3. **Features never import from `pages/`.** Pages import from features and global folders.
4. **Global folders never import from `features/`.** `components/`, `hooks/`, `services/`, `utils/`
   stay domain-agnostic. If something knows about loans or sessions, it belongs in a feature.
5. **Barrels export the public surface only** — what other features/pages need — not every file.
6. **Placement test for new code:** used by one feature -> that feature; used by several and
   domain-agnostic -> global folder; a new route -> `pages/` plus a feature for its logic. Don't
   create a global file for a single consumer or a feature folder for a single component.
7. **Components stay presentational.** HTTP calls go through `services/http.ts`; endpoint-specific
   requests and response shaping go in the feature's `api.ts`, not inline in JSX (existing inline
   `call(...)` in action handlers is tolerated; extract to `api.ts` when touching it).
8. **Keep types next to the data they describe** (`types.ts`); types exported from a barrel use
   `export type`.
9. **Tests are colocated** with the code (`foo.ts` + `foo.test.ts`). Financial math (e.g.
   `loans/emi.ts`) and API mapping always get tests; Vitest runs in `node`, so test logic, not DOM.
   Browser flows go in `e2e/`.

## Conventions

- Money: values come from the API as numbers for display only; format with `utils/format.ts`
  (`money`, `dt`). Do not do currency arithmetic in the UI beyond the indicative EMI preview; the
  server is the source of truth for amounts, rates and decisions.
- Role gating (`features/auth` `useRoles`) only hides actions for UX. The backend enforces
  authorization; never treat a hidden button as security.
- The bearer token lives in memory in `services/http.ts` only — never `localStorage`/cookies. A 401
  while signed in triggers the unauthorized handler (back to sign-in).
- Console routes are hash-based (`#/console/...`), parsed in `pages/ConsolePage.tsx`. Add a route by
  extending `parse`/`View` there.
- Styling is plain global CSS with class names; CSS files are imported once, by the shell/page that
  owns them (`landing.css` in `App.tsx`, `console.css` in `ConsolePage.tsx`, `index.css` in
  `main.tsx`). Import order matters (console rules override landing ones), so keep it.
- Landing page copy and code snippets live in `features/landing/content.ts`, not in JSX.
- Don't add libraries (router, state, UI kit, alias plugins) without asking; the codebase
  deliberately stays dependency-light.
