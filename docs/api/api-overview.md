# API Overview

Interactive docs: `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

## Conventions

**Envelope.** Every response is `ApiResponse<T>`:

```json
{ "status": "SUCCESS", "message": "...", "timestamp": "...", "data": { ... } }
{ "status": "ERROR", "message": "...", "timestamp": "...", "errors": [ { "status": 409, "code": "OFFER_ALREADY_SELECTED", "message": "...", "path": "..." } ] }
```

`errors[].code` is the stable, machine-checkable identifier — tests and API consumers should match
on that, not on `message` (message text isn't a contract).

**Authentication.** Every endpoint requires `Authorization: Bearer <token>` except
`POST /api/auth/login`, `/swagger-ui/**`, `/v3/api-docs/**`, and `/actuator/health`/`/info`. Get a
token via login (demo users in `036-seed-demo-users.sql`). Maker/checker actions and the three
admin-config endpoint groups additionally require a specific role (`401` with code
`UNAUTHENTICATED` for a missing/invalid token, `403` with code `ACCESS_DENIED` for the wrong role).

**Correlation.** Every response carries `X-Request-Id`/`X-Correlation-Id` headers (generated if not
supplied on the request); `AuditService` rows and log lines are tagged with both.

## Endpoints by module

```
Auth              POST /api/auth/login

Customer          POST /api/customers
                  GET  /api/customers/{id}

Loan products      GET /api/loan-products
                    GET /api/loan-products/{productType}

Loan lifecycle     POST /api/loans/apply
                    POST /api/loans/{reference}/submit
                    POST /api/loans/{reference}/underwriting/retry
                    GET  /api/loans/{reference}
                    GET  /api/loans/{reference}/underwriting

Documents           POST /api/loans/{reference}/documents
                    GET  /api/loans/{reference}/documents
                    POST /api/loans/{reference}/documents/{documentId}/verify
                    POST /api/loans/{reference}/documents/{documentId}/reject

KYC                 POST /api/loans/{reference}/kyc
                    GET  /api/loans/{reference}/kyc
                    POST /api/loans/{reference}/kyc/verify
                    POST /api/loans/{reference}/kyc/reject

Financial analysis  POST /api/loans/{reference}/financial-statements
                    GET  /api/loans/{reference}/financial-statements
                    GET  /api/loans/{reference}/financial-statements/{statementId}
                    POST /api/loans/{reference}/financial-statements/{statementId}/analyze
                    GET  /api/loans/{reference}/financial-statements/{statementId}/analysis   (array of runs)

Scoring             POST .../analysis-runs/{analysisRunId}/score
                    GET  .../analysis-runs/{analysisRunId}/score
                    POST /api/scorecards                          (admin)
                    GET  /api/scorecards
                    PUT  /api/scorecards/{id}                     (admin)
                    DELETE /api/scorecards/{id}                   (admin)
                    POST /api/scorecards/{id}/rules                (admin)

Decisioning         POST .../score/{scoreId}/decision
                    GET  .../score/{scoreId}/decision
                    POST /api/credit-policies                     (admin)
                    GET  /api/credit-policies
                    PUT  /api/credit-policies/{id}                 (admin)
                    DELETE /api/credit-policies/{id}                (admin)
                    POST /api/credit-policies/{id}/rules            (admin)

Authority matrix    POST /api/authority-matrix                      (admin)
                    GET  /api/authority-matrix                      (admin)
                    PUT  /api/authority-matrix/{id}                 (admin)
                    DELETE /api/authority-matrix/{id}               (admin)

Maker-checker       POST /api/credit-decisions/{decisionId}/approval-case
                    GET  /api/credit-decisions/{decisionId}/approval-case
                    GET  /api/approval-cases/{caseId}
                    POST /api/approval-cases/{caseId}/maker-decision     (ROLE_MAKER or ROLE_ADMIN)
                    POST /api/approval-cases/{caseId}/checker-decision    (ROLE_CHECKER or ROLE_ADMIN)

Offer / Sanction    POST /api/loans/{reference}/offers
                    GET  /api/loans/{reference}/offers
                    POST /api/loans/{reference}/offers/{offerId}/select
                    POST /api/loans/{reference}/sanction
                    GET  /api/loans/{reference}/sanction

Disbursement        POST /api/loans/{reference}/disbursements
                    GET  /api/loans/{reference}/disbursements

Audit               GET /api/audit/business-events?entityType=&entityId=
                    GET /api/audit/data-changes?entityType=&entityId=

Actuator            GET /actuator/health
                    GET /actuator/info
```

The full `.../analysis-runs/{analysisRunId}/score` and `.../score/{scoreId}/decision` paths are
nested under `/api/loans/{reference}/financial-statements/{statementId}` — see
[../domain/credit-scoring.md](../domain/credit-scoring.md) and
[../domain/decisioning.md](../domain/decisioning.md) for the full paths and request/response
shapes.

## Known gap for API consumers

`GET /api/loans/{reference}` never returns `LoanApplication`'s own internal UUID (only its
human-readable reference number) — so the audit endpoints can't be queried for
`entityType=LoanApplication` from outside the service layer today. Query by `Offer`, `Sanction`,
`CreditDecision`, or `ApprovalCase` id instead, all of which *are* returned to the client. See
`scripts/development/demo.sh`'s audit section for a worked example.
