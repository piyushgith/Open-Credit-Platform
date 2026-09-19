# Credit Decision Flow (Track B)

`my_docs/plan.md` §5 names this `credit-decision.png`. See
[domain/decisioning.md](../domain/decisioning.md) for the endpoint reference and
[ADR-004](../adr/ADR-004-credit-decision-engine.md)/[ADR-006](../adr/ADR-006-maker-checker.md) for
the design rationale.

```mermaid
sequenceDiagram
    actor Client
    participant Financial as financial
    participant Scoring as scoring
    participant Decision as decision
    participant Authority as authority
    participant Loan as loan

    Client->>Financial: submit + analyze statement
    Financial-->>Client: DerivedFinancialFacts, FinancialRatios, RiskIndicators
    Client->>Scoring: POST .../score
    Scoring-->>Client: Score, RiskGrade
    Client->>Decision: POST .../decision
    Decision-->>Client: CreditDecision (APPROVE/REFER/DECLINE)

    Client->>Authority: POST /credit-decisions/{id}/approval-case
    alt DECLINE, or resolved level is AUTO
        Authority->>Loan: applyCreditPipelineOutcome(APPROVED or DECLINED)
        Authority-->>Client: 409 APPROVAL_NOT_REQUIRED
    else needs sign-off
        Authority-->>Client: 201 ApprovalCase (PENDING_MAKER)
        Client->>Authority: POST .../maker-decision
        Authority-->>Client: 200 (PENDING_CHECKER)
        Client->>Authority: POST .../checker-decision
        Authority->>Loan: applyCreditPipelineOutcome(APPROVED or DECLINED)
        Authority-->>Client: 200 (APPROVED or REJECTED)
    end

    Client->>Loan: POST /loans/{ref}/offers
    Loan-->>Client: 201 Offer (only reachable once ApplicationStatus is OFFERED)
```

The `Authority -> Loan` call is Week 12's addition (`ApprovalService.applyCreditPipelineOutcome` via
`LoanApplicationService`) — before it existed, the flow ended at the `ApprovalCase`/`CreditDecision`
response and the final `POST /offers` step was unreachable from this track.
