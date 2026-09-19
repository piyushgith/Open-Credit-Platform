# Architecture Overview

## Style: modular monolith

One deployable Spring Boot application (`Open-Credit-Platform/`), one PostgreSQL database, organized
into domain-oriented Java packages under `com.opencredit.platform`. Each package is a module in the
loose, package-level sense — not yet a `spring-modulith`-verified one (`spring-modulith-starter-core`/
`-runtime` are dependencies, but no `@ApplicationModule` boundary annotations or
`ApplicationModules.verify()` test exist yet; see [ADR-008](../adr/ADR-008-event-driven-evolution.md)).

```
customer · loan · document · kyc · underwriting · financial · scoring · decision · authority
· offer · disbursement · security · audit · common
```

Why this style, not microservices: see [ADR-001](../adr/ADR-001-modular-monolith.md).

## Two decision tracks, one lifecycle owner

`LoanApplication` can reach `OFFERED`/`DECLINED` by either of two independent decision-making
tracks, but exactly one owner ever mutates `ApplicationStatus`: `LoanApplicationService`, gated by
`loan/support/ApplicationLifecycle` (`DRAFT -> SUBMITTED -> UNDERWRITING -> OFFERED/DECLINED ->
SANCTIONED -> DISBURSED`).

**Track A — the simple loan lifecycle.** `submit()` moves `DRAFT -> SUBMITTED -> UNDERWRITING` and
immediately runs a product-specific, deterministic `LoanProcessingStrategy` (e.g. a FOIR threshold
check for personal loans), which decides synchronously and sets `LoanApplication.decision` /
advances the status itself.

**Track B — financial analysis, credit scoring and decisioning**: `financial` -> `scoring` ->
`decision` -> `authority` (maker-checker). This computes a `CreditDecision` from a
`FinancialAnalysisRun`/`Score`, and either resolves that no human sign-off is needed or opens an
`authority.ApprovalCase` requiring maker-checker approval. Through Week 9, this track never wrote
back to `LoanApplication` at all — a deliberate scope decision at the time (see
`my_plan/week7-credit-rules-decision-engine-plan.md` and
`my_plan/week9-offer-sanction-disbursement-plan.md`'s "Out of scope" sections), which meant a
CreditDecision or an approved ApprovalCase never actually resulted in an Offer. **Week 12 closed
that gap**: `authority.ApprovalService` now calls `LoanApplicationService.applyCreditPipelineOutcome`
— immediately in `openCase` when a decision is declined or resolves to `AUTO` (no case needed), or
once a checker's decision makes a case terminal — to advance the same `ApplicationStatus` machine
Track A uses. `LoanApplicationService` still keeps sole ownership of the mutation itself; Track B
now also *triggers* it, the same way `OfferService`/`DisbursementService` already did for Track A's
own `sanction`/`disburse` steps.

The two tracks still don't compose on a single application: Track B's finalize call advances the
application through `SUBMITTED`/`UNDERWRITING` if it is still `DRAFT` (Track B has no `submit()` of
its own), but is a no-op once the application has already reached a terminal or later state via
whichever track got there first. Running both tracks on the same application is only meaningful as
a demonstration of each in isolation, not a real dual-decisioning workflow — see
`scripts/development/demo.sh`, which demonstrates Track A on one application and Track B (the
`my_docs/plan.md` §16 master demo scenario) on a separate one specifically so Track B's own
Offer/Sanction/Disbursement steps have something to act on.

## Cross-module calls

No event bus. A module that needs another module's data calls its repository directly (e.g.
`DecisionService` reads `scoring`'s `Score` and `financial`'s `FinancialRatio`/`RiskIndicator`
rows), and a module that needs another module to *act* calls its service directly (e.g.
`LoanApplicationService.sanction()` calls `OfferService.sanctionSelectedOffer()`). Ownership of
each entity's mutation stays with exactly one service — e.g. only `LoanApplicationService` ever
calls `LoanApplication.setStatus(...)`, even when `OfferService`/`DisbursementService` are the ones
that determined the transition is legal.

## Security and audit (Week 10)

Every endpoint except `/api/auth/login`, Swagger, and `/actuator/health`/`/info` requires a JWT
(`security` module, stateless, self-issued — no external IdP). Maker/checker actions and the three
admin-config endpoints additionally require a specific role via `@PreAuthorize`. Every important
state transition is recorded explicitly by the owning service through `AuditService` — not a
listener, not AOP; see [ADR-005](../adr/ADR-005-audit-strategy.md).

## What's not built yet

Week 11 (AI Credit Assistant) is planned (`my_plan/week11-ai-credit-assistant-plan.md`,
[ADR-007](../adr/ADR-007-ai-assistant.md)) but not implemented — there is no `ai` package in this
repository. Do not assume AI-related endpoints exist.

## See also

- [domain-boundaries.md](domain-boundaries.md) — what each module owns, aggregate roots, invariants
- [transaction-boundaries.md](transaction-boundaries.md) — where `@Transactional` lives and why
- [../domain/](../domain/) — one doc per business capability (loan lifecycle, underwriting,
  financial analysis, credit scoring, decisioning)
- [../api/api-overview.md](../api/api-overview.md) — REST surface, grouped by module
- [../diagrams/](../diagrams/) — system context, module boundaries, loan lifecycle, financial
  analysis, credit decision flow, and audit flow, as Mermaid (GitHub-rendered, not static images)
