# Decisioning & Maker-Checker Approval

See [ADR-004](../adr/ADR-004-credit-decision-engine.md) (decision engine design) and
[ADR-006](../adr/ADR-006-maker-checker.md) (approval workflow design) for the "why." This is the
operational reference.

## Credit policies and rules

Analogous to [credit-scoring.md](credit-scoring.md)'s scorecards: `CreditPolicy` (name, active
flag, partial unique index enforcing one active policy) has many `CreditRule`s, each
`factorCode operator thresholdValue` with a `severity` (`HARD`/`SOFT`).

```
POST /api/credit-policies                  admin only
GET  /api/credit-policies
PUT  /api/credit-policies/{id}
DELETE /api/credit-policies/{id}           rejected if active or already used for a decision
POST /api/credit-policies/{id}/rules
```

## Deciding a score

```
POST /api/loans/{reference}/financial-statements/{statementId}/analysis-runs/{analysisRunId}/score/{scoreId}/decision
GET  /api/loans/{reference}/financial-statements/{statementId}/analysis-runs/{analysisRunId}/score/{scoreId}/decision
```

Evaluates the active policy's rules against the score's resolved factors
(`DecisionEngine.evaluate`, see ADR-004). `UNIQUE (score_id, policy_id)` — re-deciding under the
same policy is rejected, not overwritten. Response includes `passedRules`/`failedRules`, each with
a human-readable `description` — the decision's full explanation, no separate "reason" concept.

## Maker-checker approval

```
POST /api/credit-decisions/{decisionId}/approval-case      open a case (only from a non-DECLINE decision)
GET  /api/credit-decisions/{decisionId}/approval-case

GET  /api/approval-cases/{caseId}
POST /api/approval-cases/{caseId}/maker-decision            requires ROLE_MAKER or ROLE_ADMIN
POST /api/approval-cases/{caseId}/checker-decision           requires ROLE_CHECKER or ROLE_ADMIN
```

Opening a case resolves `(productType, riskGrade, amount)` against `AuthorityMatrixEntry` rows to a
`RequiredLevel`. `AUTO` means no case is needed at all (`ApprovalNotRequiredException`) — the
seeded default matrix (`028-seed-default-authority-matrix.sql`) puts small amounts at strong risk
grades here; `scripts/development/demo.sh`'s default 500,000 loan lands in this tier, so its
maker-checker section only actually exercises the approval flow when run with a larger amount (see
that script's comments).

`maker-decision`/`checker-decision` request bodies are just `{ "outcome": "APPROVE"|"REJECT",
"comment": "..." }` — as of Week 10, actor identity and level come from the authenticated JWT
(`security/support/AuthenticatedUser`), never from the request body. Self-approval
(`SELF_APPROVAL_NOT_ALLOWED`) and insufficient authority (`INSUFFICIENT_APPROVAL_AUTHORITY`) are
checked against that authenticated identity.

## Configuring the authority matrix

```
POST /api/authority-matrix                 admin only
GET  /api/authority-matrix
PUT  /api/authority-matrix/{id}
DELETE /api/authority-matrix/{id}
```

Entries match on optional `productType`/`riskGrade`/amount range, ordered by `matchOrder`; the
first match wins. A catch-all entry (no criteria) should always exist so resolution can never fail
to find one — the seeded default does this at `matchOrder = 100`.
