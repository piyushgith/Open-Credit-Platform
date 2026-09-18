# Week 4 Implementation Plan — Financial Statement Engine

## Context

Weeks 2-3 (`my_plan/week2-*`, `my_plan/week3-*`) shipped `Customer`/`LoanProduct`/`LoanApplication`, `LoanDocument`,
`KycCase` and `UnderwritingCase`/`UnderwritingAttempt`. Per [my_docs/plan.md](../my_docs/plan.md) Week 4, this stage
adds the financial analysis engine: raw financial data → normalized facts → derived facts → (ratios in Week 5).

## Scope note: ratios/categories deferred to Week 5

The Week 4 prompt's 7-step "processing order" mentions ratios (step 5) and categories (step 6), but:
- Week 4's own **Build** list only names `FinancialStatement`, `FinancialPeriod`, `FinancialLineItem`,
  `DerivedFinancialFact`, `FinancialAnalysisRun`.
- `FinancialRatio` and `FinancialCategory` are listed under the Financial domain (§7) but Week 5's Claude Prompt
  explicitly says it will **"Extend the financial analysis engine with: financial ratios, financial categories,
  risk indicators"** — confirming those are additive, not part of Week 4.
- All of Week 4's own worked examples (Working Capital, Gross Profit, Debt) are pure addition/subtraction — no
  division appears until Week 5's ratio list (Current Ratio, Debt/EBITDA, DSCR, ...).

So Week 4 implements steps 1-4 (extract → normalize → derive → persist derived facts) only. No division, so the
"zero denominator" test case doesn't apply yet; the Week 4 analogue is a **missing child fact**, which is covered.

## Design decisions

| Decision | Choice |
|---|---|
| Line items supported | Fixed `FinancialLineItemCode` enum (no generic/configurable schema): `CURRENT_ASSETS`, `NON_CURRENT_ASSETS`, `CURRENT_LIABILITIES`, `NON_CURRENT_LIABILITIES`, `EQUITY`, `REVENUE`, `COST_OF_GOODS_SOLD`, `OPERATING_EXPENSES`, `DEPRECIATION_AMORTIZATION`, `INTEREST_EXPENSE`, `TAX_EXPENSE`, `LONG_TERM_DEBT`, `SHORT_TERM_DEBT`. Composite items (e.g. total assets) are deliberately **not** raw codes, so there's no way to "blindly trust a supplied total" — it can only come from calculation. |
| Derived facts (Week 4) | `TOTAL_ASSETS`, `TOTAL_LIABILITIES`, `WORKING_CAPITAL`, `TOTAL_DEBT`, `GROSS_PROFIT` (level 1, from raw line items only); `OPERATING_PROFIT` (from `GROSS_PROFIT`); `EBITDA`, `NET_PROFIT` (from `OPERATING_PROFIT`) — a real 2-level dependency chain, so calculation ordering is a genuine constraint, not just a formality |
| Period modeling | `FinancialPeriod` is 1:1-owned by `FinancialStatement` (created together, never shared/reused) — captures `periodLabel`, `periodType` (`ACTUAL`/`AUDITED`/`PROVISIONAL`/`PROJECTED`), `startDate`, `endDate` |
| Entity relationships | Plain UUID FK columns everywhere (no JPA `@ManyToOne`/`@OneToOne`), matching every existing module (`LoanDocument.applicationId`, `UnderwritingAttempt.underwritingCaseId`, ...) — sidesteps lazy-loading/N+1 entirely |
| Missing values | Raw values are simply absent rows, never a stored `null`/zero placeholder. A derived fact whose required inputs are (transitively) missing is **not computed** (no row persisted) rather than defaulting to zero or throwing — missing propagates naturally since a dependent formula looks up an absent parent result |
| Money math | `BigDecimal`, scale 2, `RoundingMode.HALF_UP`, rounded after every arithmetic step — same convention as `EmiCalculator.MONEY_SCALE` |
| Auditability of runs | Re-analyzing a statement creates a new immutable `FinancialAnalysisRun` + fresh `DerivedFinancialFact` rows rather than overwriting — same "never overwrite, append a new attempt" pattern as `UnderwritingAttempt` |
| Analysis run lifecycle | No status enum: the engine is pure/deterministic with no external calls, so (unlike `UnderwritingAttempt`) there's no in-progress/terminal state to track — just `runAt` |

## Schema changes (Liquibase, additive changesets)

- `010-create-financial-period.sql`, `011-create-financial-statement.sql`, `012-create-financial-line-item.sql`,
  `013-create-financial-analysis-run.sql`, `014-create-derived-financial-fact.sql`.
- Uniqueness: `(statement_id, line_item_code)` on line items, `(analysis_run_id, fact_code)` on derived facts —
  each line item/fact appears at most once per statement/run.

## New module: `financial`

- `model`: `FinancialPeriodType`, `FinancialLineItemCode`, `DerivedFinancialFactCode` (enums) +
  `FinancialPeriod`, `FinancialStatement`, `FinancialLineItem`, `FinancialAnalysisRun`, `DerivedFinancialFact`.
- `repository`: one Spring Data repository per entity above.
- `support.DerivedFactCalculator`: pure, stateless `@Component` (mirrors `EmiCalculator`) — the actual formula
  engine, unit-testable without Spring/DB.
- `dto`: `FinancialPeriodRequest`/`Response`, `FinancialLineItemRequest`/`Response`, `FinancialStatementRequest`/
  `Response`, `DerivedFinancialFactResponse`, `FinancialAnalysisRunResponse`.
- `exception`: `FinancialStatementNotFoundException`, `InvalidFinancialPeriodException` (end date not after start).
- `FinancialStatementService` (single service, like `UnderwritingService` owns both case + attempts): submit /
  list / get statements, plus `analyze` / `getAnalysisRuns`.
- `FinancialStatementController`: `POST/GET /api/loans/{reference}/financial-statements`,
  `GET .../{statementId}`, `POST .../{statementId}/analyze`, `GET .../{statementId}/analysis`.

## Exception handling additions

`FinancialStatementNotFoundException` → 404, `InvalidFinancialPeriodException` → 422, added to
`GlobalExceptionHandler` following the existing per-exception handler pattern.

## Tests

- `DerivedFactCalculatorTest` (pure, no Spring) — full calculation with all inputs present; a missing level-1
  input skips only its dependents (others still compute); negative results propagate correctly (e.g. COGS >
  Revenue → negative `GROSS_PROFIT` → negative `OPERATING_PROFIT`/`EBITDA`/`NET_PROFIT`, not clamped to zero);
  zero is treated as a present value, not a missing one; rounding at scale 2 `HALF_UP`.
- `FinancialStatementServiceTest` — submit persists statement/period/line items; invalid period dates rejected;
  not-found; `analyze` persists a run + facts derived from stored line items; a second `analyze` call creates a
  second, independent run (history preserved, not overwritten).
- `FinancialStatementIntegrationTest` — full stack: create customer + application, submit a statement with a
  realistic line-item set, analyze it, assert the derived facts in the JSON response, assert re-analysis appends
  a second run.

## Out of scope (later weeks)

Financial ratios, financial categories, risk indicators (Week 5 — extends this engine). Multi-currency, statement
amendment/versioning beyond re-analysis, source document upload (documents already exist as a separate module).
