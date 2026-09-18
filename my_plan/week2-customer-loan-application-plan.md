# Week 2 Implementation Plan — Customer + Loan Application Lifecycle

## Context

Week 1's vertical slice (`my_plan/backend-implementation-plan.md`) shipped a single `POST /api/loans/apply`
that instantly decides and persists a loan application as `PROCESSED`. Per
[my_docs/plan.md](../my_docs/plan.md) Week 2, this stage adds:

1. A `Customer` aggregate (own module, own table).
2. A `LoanProduct` catalog (own table, seeded with PERSONAL/VEHICLE bounds).
3. A real application lifecycle: `DRAFT → SUBMITTED → UNDERWRITING → (OFFERED | DECLINED) → SANCTIONED → DISBURSED`,
   with illegal transitions rejected.

Decisions confirmed with you:

| Decision | Choice |
|---|---|
| Decisioning vs. lifecycle | Full workflow — `apply` only creates a `DRAFT`; a new `submit` endpoint runs the existing strategy decisioning at `UNDERWRITING` and lands on `OFFERED`/`DECLINED` (or parks at `UNDERWRITING` on `REFERRED`) |
| Customer linkage | Required FK — `LoanRequest.applicantName` is replaced by `LoanRequest.customerId`; `apply` fails if the customer doesn't exist |

## Schema changes (Liquibase, additive changesets)

- `002-create-customer.sql`: `customer` table (`full_name`, `email`, `phone_number`, `date_of_birth`,
  `pan_number`, unique on email/phone/PAN).
- `003-create-loan-product.sql`: `loan_product` table (`product_type` unique, min/max amount, min/max tenure, `active`).
- `004-seed-loan-products.sql`: seeds PERSONAL and VEHICLE rows with fixed UUIDs.
- `005-alter-loan-application-lifecycle.sql`: adds `customer_id`/`product_id` FKs (NOT NULL after backfill-free
  dev alter), drops `NOT NULL` on `decision`/`decision_details` (now populated only after underwriting runs).

## Domain changes

- `ApplicationStatus`: `PROCESSED` → `DRAFT, SUBMITTED, UNDERWRITING, OFFERED, DECLINED, SANCTIONED, DISBURSED`.
- New `loan/support/ApplicationLifecycle`: pure, stateless transition guard (mirrors `EmiCalculator`'s style),
  throws `IllegalApplicationTransitionException` (409) on an illegal move.
- `LoanApplication` entity: adds `customerId`, `productId`; `decision`/`decisionDetails` become nullable.
- `LoanRequest`: `applicantName` (String) → `customerId` (UUID).
- `LoanResponse`: adds `status` (`ApplicationStatus`) so every response reflects lifecycle state, not just decision.

## New module: `customer`

`Customer` (model/repository/dto/exception) + `CustomerService` + `CustomerController`:
`POST /api/customers`, `GET /api/customers/{id}`. Duplicate email/phone/PAN → 409; not found → 404.

## Loan product catalog

`LoanProduct` (model/repository) + `LoanProductService` + `LoanProductController`:
`GET /api/loan-products`, `GET /api/loan-products/{productType}`. `apply` validates requested
amount/tenure against the product's bounds (`LoanProductConstraintViolationException` → 422).

## Loan endpoints (`LoanController`)

| Method | Path | Transition | Body |
|---|---|---|---|
| `POST` | `/apply` | (none) → `DRAFT` | full request incl. product-specific fields |
| `POST` | `/{reference}/submit` | `DRAFT → SUBMITTED → UNDERWRITING → OFFERED\|DECLINED` | — |
| `POST` | `/{reference}/sanction` | `OFFERED → SANCTIONED` | — |
| `POST` | `/{reference}/disburse` | `SANCTIONED → DISBURSED` | — |
| `GET` | `/{reference}` | (read) | — |

`submit` reconstructs the stored `LoanRequest` from `requestDetails` JSONB (same `ObjectMapper`, so the
discriminator round-trips) and runs the existing `LoanProcessingStrategy`, unchanged.

## Exception handling additions

`CustomerNotFoundException` (404), `DuplicateCustomerException` (409), `LoanProductNotFoundException` (404),
`LoanProductConstraintViolationException` (422), `IllegalApplicationTransitionException` (409).

## Tests

- `ApplicationLifecycleTest` — every legal/illegal transition, terminal states.
- `CustomerServiceTest`, `CustomerControllerTest` — register, duplicate conflicts, not found.
- `LoanProductServiceTest` — catalog lookup, inactive/unknown product.
- Update `LoanRequestJsonTest`, `PersonalLoanStrategyTest`, `VehicleLoanStrategyTest`, `LoanControllerTest`:
  `applicantName` → `customerId`.
- Rewrite `LoanApplicationIntegrationTest`: register a customer, apply (expect `DRAFT`, no decision fields),
  submit (expect decision fields + `OFFERED`/`DECLINED`), sanction, disburse, and assert illegal transitions
  (e.g. `sanction` before `submit`) return 409.

## Out of scope (later weeks)

KYC/documents (Week 3), authority matrix / maker-checker for `REFERRED` applications (Week 8), offer/sanction
document generation (Week 9). `REFERRED` decisions simply park at `UNDERWRITING` for now — no automatic
progression past it.
