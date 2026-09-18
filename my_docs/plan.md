# Open Credit Platform

## 12-Week Build Plan, GitHub Structure & Claude Code Playbook

**Technology:** Java 25, Spring Boot, PostgreSQL
**Architecture:** Modular Monolith
**Goal:** Build a serious open-source banking/credit platform that demonstrates Senior Tech Lead / Solution Architect capability.

---

# 1. Project Goal

Build a production-style credit decisioning platform covering:

```text
Loan Application
      ↓
Customer / KYC
      ↓
Financial Data
      ↓
Financial Analysis
      ↓
Credit Scoring
      ↓
Credit Rules
      ↓
Credit Decision
      ↓
Offer & Sanction
      ↓
Disbursement
      ↓
Audit
```

AI is an assistant, not the final decision maker.

```text
Deterministic Rules + Scorecard
             ↓
       Credit Decision
             ↓
       AI Assistant
             ↓
 Explanation / Anomaly Detection /
 Suggested Questions
```

---

# 2. Career Objective

The project should demonstrate:

* Java 25
* Spring Boot
* PostgreSQL
* REST API design
* Domain-driven design
* Modular monolith architecture
* Transaction management
* Concurrency
* Idempotency
* Financial calculations
* Credit scoring
* Rule engines
* Authority matrix
* Maker-checker
* Audit architecture
* Security
* Testing
* Observability
* Docker
* AI integration
* System design thinking

The objective is NOT to build the largest application.

The objective is to demonstrate:

> "I can design and build a complex financial system while keeping the architecture simple."

---

# 3. Non-Negotiable Engineering Principles

These rules apply throughout the project.

1. Java only.
2. Java 25.
3. Spring Boot.
4. PostgreSQL.
5. Modular monolith first.
6. No microservices unless there is a demonstrated reason.
7. No speculative abstractions.
8. No generic framework-building.
9. No unnecessary interfaces.
10. No God classes.
11. No giant service implementations.
12. No unnecessary private helper methods.
13. Keep controllers thin.
14. Keep business logic in the appropriate domain/application layer.
15. Database constraints are part of business correctness.
16. Every important financial calculation requires tests.
17. Every state transition requires validation.
18. Financial decisions must be deterministic and explainable.
19. AI must never silently override deterministic credit decisions.
20. All important changes require auditability.
25. Avoid N+1 queries.
22. Avoid unnecessary database calls.
23. Use transactions deliberately.
24. Use idempotency for externally triggered operations.
25. Do not introduce Kafka/Redis unless there is a real requirement.
26. Do not split modules into microservices merely for resume value.
27. Prefer simple code over clever code.
28. Accuracy > speed.
29. Verification > assumption.
30. If requirements are ambiguous, stop and ask.

---

# 4. Target Architecture

```text
                    REST API
                       │
                       ▼
              Application Layer
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
     Customer        Loan          Underwriting
        │              │              │
        └──────────────┼──────────────┘
                       │
                       ▼
                Financial Module
                       │
                       ▼
                Credit Module
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
     Scoring         Rules       Authority
        │              │              │
        └──────────────┼──────────────┘
                       ▼
                 Decision Module
                       │
              ┌────────┴────────┐
              ▼                 ▼
          Offer/Sanction    Audit
              │
              ▼
         Disbursement
```

---

# 5. Repository Structure

```text
open-credit-platform/
│
├── README.md
├── LICENSE
├── CONTRIBUTING.md
├── SECURITY.md
├── CLAUDE.md
├── pom.xml
├── docker-compose.yml
├── .gitignore
│
├── docs/
│   │
│   ├── architecture/
│   │   ├── overview.md
│   │   ├── domain-boundaries.md
│   │   ├── transaction-boundaries.md
│   │   └── deployment.md
│   │
│   ├── adr/
│   │   ├── ADR-001-modular-monolith.md
│   │   ├── ADR-002-postgresql.md
│   │   ├── ADR-003-financial-calculation-engine.md
│   │   ├── ADR-004-credit-decision-engine.md
│   │   ├── ADR-005-audit-strategy.md
│   │   ├── ADR-006-maker-checker.md
│   │   ├── ADR-007-ai-assistant.md
│   │   └── ADR-008-event-driven-evolution.md
│   │
│   ├── diagrams/
│   │   ├── system-context.png
│   │   ├── module-boundaries.png
│   │   ├── loan-lifecycle.png
│   │   ├── financial-analysis.png
│   │   ├── credit-decision.png
│   │   └── audit-flow.png
│   │
│   ├── domain/
│   │   ├── loan-lifecycle.md
│   │   ├── underwriting.md
│   │   ├── financial-analysis.md
│   │   ├── credit-scoring.md
│   │   └── decisioning.md
│   │
│   └── api/
│       └── api-overview.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── opencredit/
│   │   │           └── platform/
│   │   │
│   │   │               ├── customer/
│   │   │               ├── loan/
│   │   │               ├── document/
│   │   │               ├── kyc/
│   │   │               ├── underwriting/
│   │   │               ├── financial/
│   │   │               ├── scoring/
│   │   │               ├── decision/
│   │   │               ├── offer/
│   │   │               ├── disbursement/
│   │   │               ├── audit/
│   │   │               ├── security/
│   │   │               └── shared/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│       └── java/
│           └── com/
│               └── opencredit/
│                   └── platform/
│
├── docker/
│   └── postgres/
│
└── scripts/
    └── development/
```

---

# 6. Package Structure

Each domain should remain internally simple.

Example:

```text
financial/
├── controller/
├── service/
├── model/
├── repository/
└── mapper/
```

Do not automatically create:

```text
factory/
strategy/
builder/
adapter/
facade/
orchestrator/
processor/
handler/
resolver/
provider/
```

Only introduce these when the domain actually requires them.

---

# 7. Core Domain

## Customer

```text
Customer
CustomerAddress
CustomerEmployment
CustomerFinancialProfile
```

## Loan

```text
LoanApplication
LoanProduct
LoanApplicationStatus
LoanPurpose
```

## Document

```text
LoanDocument
DocumentType
DocumentStatus
```

## KYC

```text
KycCase
KycResult
KycStatus
```

## Underwriting

```text
UnderwritingCase
UnderwritingAttempt
UnderwritingStatus
```

## Financial

```text
FinancialStatement
FinancialPeriod
FinancialLineItem
DerivedFinancialFact
FinancialRatio
FinancialCategory
FinancialAnalysisRun
```

## Scoring

```text
Scorecard
ScorecardRule
Score
RiskGrade
RiskFactor
```

## Decision

```text
CreditPolicy
CreditRule
RuleResult
CreditDecision
DecisionReason
AuthorityMatrix
```

## Offer

```text
Offer
Sanction
OfferStatus
```

## Disbursement

```text
Disbursement
DisbursementTranche
DisbursementStatus
```

## Audit

```text
AuditEvent
DataChange
BusinessEvent
```

---

# 8. 12-Week Roadmap

# Week 1 — Architecture & Foundation

## Goals

* Finalize requirements.
* Define bounded modules.
* Define application lifecycle.
* Create initial database design.
* Create ADRs.
* Create Spring Boot project.

## Deliverables

```text
README.md
CLAUDE.md
ADR-001-modular-monolith.md
ADR-002-postgresql.md
domain-boundaries.md
system-context.png
module-boundaries.png
```

## Claude Prompt

```text
You are the principal architect for this project.

We are building an open-source credit decisioning platform using:

- Java 25
- Spring Boot
- PostgreSQL
- Flyway
- REST APIs
- Modular monolith architecture

Before writing code:

1. Inspect the repository.
2. Identify existing files and structure.
3. Define the business lifecycle.
4. Define domain/module boundaries.
5. Define transaction boundaries.
6. Identify aggregate roots.
7. Identify important database constraints.
8. Identify potential concurrency problems.
9. Identify areas where microservices would be unnecessary.

Do NOT generate implementation code.

Produce:
- architecture overview
- module boundaries
- domain model
- transaction boundaries
- important invariants
- initial database model
- ADR recommendations

Keep the design simple.

Do not introduce abstractions without a concrete requirement.
```

---

# Week 2 — Customer + Loan Application

## Build

```text
Customer
Loan Product
Loan Application
Application Number
Application Lifecycle
```

Lifecycle:

```text
DRAFT
   ↓
SUBMITTED
   ↓
UNDERWRITING
   ↓
OFFERED
   ↓
SANCTIONED
   ↓
DISBURSED
```

Do not allow illegal transitions.

## Claude Prompt

```text
Implement Week 2 only.

Scope:

- Customer
- LoanProduct
- LoanApplication
- Application lifecycle
- REST APIs
- PostgreSQL migrations
- Validation
- Unit tests
- Integration tests

Rules:

- Java 25
- Spring Boot
- PostgreSQL
- Flyway
- Thin controllers
- Simple service implementations
- No speculative abstractions
- No unnecessary private helper methods
- No generic framework
- No unrelated refactoring

Before changing code:
1. Inspect the repository.
2. Explain the planned changes.
3. Implement only after the plan is clear.

Every state transition must be validated.

Add tests for:
- valid transition
- invalid transition
- duplicate application
- validation failure
- persistence
```

---

# Week 3 — Documents + KYC + Underwriting

## Build

```text
LoanDocument
KycCase
UnderwritingCase
UnderwritingAttempt
```

Important rule:

```text
One Application
       ↓
Multiple Underwriting Attempts
```

Only one active underwriting attempt.

## Claude Prompt

```text
Implement Week 3.

Scope:

- Loan documents
- KYC case
- Underwriting case
- Multiple underwriting attempts
- Active attempt constraint
- Underwriting lifecycle
- APIs
- Flyway migrations
- Tests

Important business rules:

1. One application can have multiple underwriting attempts.
2. Only one underwriting attempt can be active.
3. Every underwriting attempt must have a cycle number.
4. Underwriting cannot start for an invalid application state.
5. Failed underwriting must remain historically visible.
6. A successful underwriting must be linked to the application.
7. Do not overwrite previous underwriting attempts.

Check database constraints and transaction boundaries.

Do not refactor unrelated code.
```

---

# Week 4 — Financial Statement Engine

This is a major portfolio component.

## Build

```text
FinancialStatement
FinancialPeriod
FinancialLineItem
DerivedFinancialFact
FinancialAnalysisRun
```

Pipeline:

```text
Raw Data
   ↓
Normalized Facts
   ↓
Derived Facts
   ↓
Ratios
```

Example:

```text
Current Assets
Current Liabilities
       ↓
Working Capital

Revenue
COGS
       ↓
Gross Profit

Debt
EBITDA
       ↓
Debt / EBITDA
```

## Claude Prompt

```text
Implement Week 4: Financial Analysis Engine.

The engine must follow this exact processing order:

1. Extract raw financial line items.
2. Normalize values.
3. Calculate derived financial facts.
4. Persist derived facts.
5. Calculate ratios from derived facts.
6. Calculate financial categories.
7. Persist the analysis run.

Never calculate ratios directly from raw workbook values when a derived fact exists.

Derived values must be calculated from their defined child values.

Example:

totalAssets = currentAssets + nonCurrentAssets

Do not blindly trust a supplied totalAssets value.

Requirements:

- Support multiple financial periods.
- Support actual/audited/provisional/projected periods.
- Preserve source values.
- Preserve calculated values.
- Make calculations deterministic.
- Use BigDecimal for financial calculations.
- Define rounding rules explicitly.
- Add extensive unit tests.

Test:
- normal calculations
- null values
- zero denominators
- negative values
- missing child facts
- rounding
- multiple years
- calculation ordering

Do not add AI.
Do not add Kafka.
Do not add Redis.
Do not refactor unrelated modules.
```

---

# Week 5 — Financial Ratios + Risk Indicators

## Ratios

Start with:

```text
Current Ratio
Quick Ratio
Debt / Equity
Debt / EBITDA
Interest Coverage
Gross Margin
EBITDA Margin
Net Margin
ROA
ROE
DSCR
```

## Risk indicators

```text
Declining Revenue
Declining EBITDA
Negative Cash Flow
High Leverage
Low Liquidity
Weak Interest Coverage
```

## Claude Prompt

```text
Implement Week 5.

Extend the financial analysis engine with:

- financial ratios
- financial categories
- risk indicators

Requirements:

1. Every formula must be explicitly documented.
2. Every ratio must have unit tests.
3. Handle zero denominators safely.
4. Do not silently convert missing data into zero.
5. Preserve calculation precision.
6. Define rounding behavior.
7. Keep formulas deterministic.
8. Avoid a giant FinancialAnalysisService.

If the current implementation is becoming too large:
STOP and propose a simpler design.

Do not create abstractions just to reduce line count.

Update documentation and tests.
```

---

# Week 6 — Credit Scoring

## Build

```text
Scorecard
ScorecardRule
Score
RiskGrade
RiskFactor
```

Example:

```text
Debt/EBITDA
Interest Coverage
DSCR
Revenue Growth
Current Ratio
```

Output:

```json
{
  "score": 742,
  "riskGrade": "B",
  "riskFactors": [
    "High leverage",
    "Strong interest coverage"
  ]
}
```

## Claude Prompt

```text
Implement Week 6: Credit Scoring.

Build a deterministic scorecard engine.

Input:

- customer data
- loan data
- financial analysis
- financial ratios

Output:

- total score
- risk grade
- contributing factors
- failed factors

Requirements:

- deterministic results
- explainable scoring
- configurable scorecard rules
- BigDecimal where financially appropriate
- no AI
- no external rules engine dependency
- no reflection-based magic
- no generic expression language unless required

Design the simplest maintainable implementation.

Tests must verify:
- score calculation
- boundary values
- missing values
- conflicting factors
- risk-grade boundaries
- deterministic repeatability
```

---

# Week 7 — Credit Rules + Decision Engine

This is the heart of the platform.

## Build

```text
CreditPolicy
CreditRule
RuleCondition
RuleResult
CreditDecision
DecisionReason
```

Possible outcomes:

```text
APPROVE
REFER
DECLINE
```

## Example

```text
Score >= 700
AND
DSCR >= 1.5
AND
Debt/EBITDA <= 4
```

## Claude Prompt

```text
Implement Week 7: Credit Decision Engine.

Build a deterministic credit decision engine.

Inputs:

- Loan Application
- Financial Analysis
- Credit Score
- Risk Grade
- Credit Policy

Outputs:

- APPROVE
- REFER
- DECLINE
- decision reasons
- failed rules
- passed rules
- required authority

Important:

The engine must be deterministic.

The decision must be explainable.

Do not allow AI to make or override the decision.

Do not create a generic rule-engine framework.

Implement only the rules required by this domain.

Protect against:
- rule ordering bugs
- missing values
- contradictory rules
- duplicate evaluation
- incorrect thresholds

Add comprehensive tests.

Document every decision rule.
```

---

# Week 8 — Authority Matrix + Maker Checker

## Build

```text
AuthorityMatrix
ApprovalLevel
Maker
Checker
ApprovalDecision
```

Example:

```text
Loan Amount
Risk Grade
Product
Branch
Authority Level
```

Flow:

```text
Maker
  ↓
Decision
  ↓
Checker
  ↓
Approved / Rejected
```

## Claude Prompt

```text
Implement Week 8.

Add:

- authority matrix
- maker-checker
- approval levels
- approval workflow

Business rules:

1. Maker cannot approve their own decision.
2. Checker must have sufficient authority.
3. Approval authority depends on configured criteria.
4. Rejected approvals remain in history.
5. Approval cannot be performed twice.
6. Concurrent approval attempts must be handled safely.
7. State transitions must be transactional.

Focus on correctness.

Add tests for concurrency-sensitive scenarios.

Do not introduce distributed locking.

Use PostgreSQL/database constraints or appropriate transactional locking where justified.

Explain the transaction strategy before implementation.
```

---

# Week 9 — Offer, Sanction & Disbursement

## Build

```text
Offer
Sanction
Disbursement
DisbursementTranche
```

Business flow:

```text
Underwriting
      ↓
Offer
      ↓
Sanction
      ↓
Disbursement
```

Important:

```text
One successful underwriting
        ↓
One or more offers

Selected offer
        ↓
One or more disbursements
```

## Claude Prompt

```text
Implement Week 9.

Add:

- Offer
- Sanction
- Offer selection
- Disbursement
- Multiple disbursement tranches

Business rules:

1. Offer can only be created from successful underwriting.
2. Only valid offers can be selected.
3. Sanction requires an eligible offer.
4. Disbursement requires sanctioned offer.
5. Total disbursed amount cannot exceed sanctioned amount.
6. Duplicate disbursement requests must be prevented.
7. Each tranche must be independently traceable.
8. All monetary calculations must use BigDecimal.
9. Important state transitions must be transactional.

Add integration tests covering the complete lifecycle.

Do not refactor unrelated modules.
```

---

# Week 10 — Audit + Security + Observability

## Audit

Two levels:

### Business audit

```text
UNDERWRITING_STARTED
UNDERWRITING_COMPLETED
DECISION_CREATED
OFFER_CREATED
OFFER_ACCEPTED
SANCTION_APPROVED
DISBURSEMENT_CREATED
```

### Data audit

```text
Entity
Field
Old Value
New Value
User
Timestamp
Request ID
Correlation ID
```

## Security

```text
JWT
Roles
Authorization
Maker
Checker
Credit Officer
Admin
```

## Claude Prompt

```text
Implement Week 10.

Add:

1. Business audit
2. Data-change audit
3. Security
4. Correlation/request identifiers
5. Basic structured logging
6. Actuator health endpoints

Audit requirements:

- who changed data
- when
- entity
- entity ID
- field
- old value
- new value
- request ID
- correlation ID

Business events must be separately identifiable from raw data changes.

Audit records must not be casually editable/deletable through normal APIs.

Security requirements:

- authentication
- authorization
- maker/checker separation
- endpoint protection
- service-level authorization where required

Do not log:
- passwords
- JWT tokens
- sensitive credentials
- unnecessary personal data

Keep the implementation simple.
```

---

# Week 11 — AI Credit Assistant

AI comes LAST.

AI is not the credit decision engine.

## AI capabilities

```text
Financial analysis explanation
Anomaly identification
Risk-factor explanation
Missing-information suggestions
Credit memo draft
Suggested questions for analyst
```

Example:

```text
Decision:
REFER

Deterministic reasons:
- Debt/EBITDA = 4.8x
- Policy threshold = 4.0x

AI Assistant:
"Leverage exceeds the configured threshold.
Consider requesting an updated debt schedule
and clarification of the increase in borrowings."
```

## Claude Prompt

```text
Implement Week 11: AI Credit Assistant.

Important architecture rule:

AI must NEVER:
- approve a loan
- reject a loan
- modify the credit score
- override a credit rule
- modify financial facts
- modify audit history

AI may:

- explain deterministic decisions
- summarize financial analysis
- identify potential anomalies
- suggest questions for a credit analyst
- draft a credit memo
- explain risk factors

The deterministic engine remains the source of truth.

Design an AI boundary around the existing decision engine.

The AI should receive structured facts rather than unrestricted database access.

AI output must be clearly marked as AI-generated.

Do not make the application dependent on AI for normal credit decisions.

Add tests for:
- AI unavailable
- malformed response
- timeout
- unsupported recommendation
- deterministic decision remaining unchanged
```

---

# Week 12 — Production Hardening + Portfolio

This week is NOT about adding features.

It is about proving the project works.

## Tasks

```text
Performance
Security
Concurrency
Transactions
Database indexing
N+1 detection
Error handling
API consistency
Testing
Documentation
Architecture diagrams
README
Demo data
Docker
CI
```

---

# 9. Week 12 Claude Prompt — Full Adversarial Review

```text
Act as a principal engineer performing a pre-production review.

Do NOT modify code yet.

Inspect the entire repository.

Assume this application will be used by a financial institution.

Find:

1. Business logic bugs
2. Financial calculation errors
3. Incorrect transaction boundaries
4. Race conditions
5. Double approval
6. Double disbursement
7. Idempotency failures
8. Illegal state transitions
9. Missing database constraints
10. N+1 queries
11. Missing indexes
12. Incorrect JPA mappings
13. Lazy-loading problems
14. Security vulnerabilities
15. Authorization bypasses
16. Audit gaps
17. Sensitive logging
18. Incorrect BigDecimal usage
19. Null-handling problems
20. Incorrect rounding
21. Duplicate business logic
22. God classes
23. Excessive private helper methods
24. Unnecessary interfaces
25. Speculative abstractions
26. Over-engineering
27. Dead code
28. Missing tests
29. Weak integration tests
30. AI boundary violations

Compare implementation against:

- README
- ADRs
- domain documentation
- database schema
- tests

Output ONLY a table initially:

| Severity | Class/File | Line | Method | Problem | Why It Matters | Recommended Fix |

Severity:

CRITICAL
MAJOR
MINOR

Do not fix anything.

Do not rewrite anything.

Do not invent problems.

Only report verified findings.
```

---

# 10. Sequential Review Prompt

After reviewing the entire project, use this repeatedly.

```text
We are fixing findings from the previous review.

Take ONLY the following finding:

<PASTE ONE FINDING>

Before modifying code:

1. Inspect the relevant implementation.
2. Verify whether the finding is actually valid.
3. Identify the smallest safe fix.
4. Explain the fix.
5. Identify affected tests.
6. Identify possible regressions.

Then implement only that fix.

Rules:

- Do not refactor unrelated code.
- Do not create new abstractions unless necessary.
- Do not rename unrelated classes.
- Do not change public APIs unless required.
- Do not modify database schema unless required.
- Preserve existing behavior except where the finding requires correction.
- Add/update tests.
- Run relevant tests.
- Report exactly what changed.

Never create a git commit.
```

---

# 11. Financial Calculation Review Prompt

This deserves its own review.

```text
Act as a senior financial-software engineer.

Review ONLY the financial analysis implementation.

Verify every calculation against its documented formula.

Check:

- BigDecimal usage
- scale
- rounding
- division by zero
- null handling
- negative values
- sign conventions
- derived values
- raw values
- ratio dependencies
- calculation order
- period handling
- actual/audited/provisional/projected periods
- duplicate calculations
- persisted results

For every formula produce:

| Formula | Implementation | Correct? | Problem | Fix |

Do not modify code.

Do not assume the implementation is correct.

Trace calculations from raw facts to derived facts to ratios.
```

---

# 12. Database Review Prompt

```text
Review the PostgreSQL schema as a banking application database.

Check:

- primary keys
- foreign keys
- unique constraints
- check constraints
- indexes
- nullable columns
- monetary types
- timestamps
- optimistic locking
- state constraints
- duplicate prevention
- active-record constraints
- audit integrity
- query patterns
- N+1 risks

Identify constraints that belong in the database rather than Java.

Do not modify anything.

Output:

| Table | Problem | Risk | Recommended Constraint/Index |
```

---

# 13. Concurrency Review Prompt

```text
Perform a concurrency review.

Focus on:

- maker/checker approval
- underwriting activation
- offer selection
- sanction
- disbursement
- duplicate requests
- retries
- concurrent updates
- optimistic locking
- database locking
- transaction isolation

Create concrete race-condition scenarios.

Example:

Request A:
approve(application)

Request B:
approve(application)

Determine exactly what happens.

Do not modify code.

Report:

| Scenario | Current Behavior | Risk | Recommended Fix |
```

---

# 14. System Design Interview Prompt

Once the project is stable:

```text
Use this repository as the source of truth.

Act as a FAANG-level system design interviewer.

Ask me to design this platform at:

1. 10K applications/year
2. 1M applications/year
3. 10M applications/year

Challenge:

- database scaling
- partitioning
- caching
- asynchronous processing
- event-driven architecture
- service boundaries
- consistency
- idempotency
- observability
- disaster recovery
- security
- audit
- financial correctness

Do not immediately give me the answer.

Ask one question at a time.

Evaluate my architectural reasoning after each answer.
```

---

# 15. README Structure

The final README should contain:

```text
# Open Credit Platform

## Problem

## Goals

## Non-Goals

## Architecture

## Domain Model

## Loan Lifecycle

## Financial Analysis

## Credit Scoring

## Decision Engine

## Authority Matrix

## Audit

## AI Assistant

## Security

## Database Design

## Transaction Strategy

## Concurrency

## Testing

## Running Locally

## API Examples

## Architecture Decisions

## Future Evolution

## Why Modular Monolith?

## When Microservices Would Make Sense

## Scaling Strategy

## Limitations

## Roadmap
```

---

# 16. Demo Scenario

Create one complete demo:

```text
SME Customer
     ↓
Loan Application
     ↓
Documents
     ↓
KYC
     ↓
Financial Statements
     ↓
Financial Analysis
     ↓
Ratios
     ↓
Credit Score
     ↓
Credit Rules
     ↓
REFER
     ↓
Authority Matrix
     ↓
Checker Review
     ↓
APPROVE
     ↓
Offer
     ↓
Sanction
     ↓
Disbursement
     ↓
Audit History
```

This should be executable locally with sample data.

---

# 17. Portfolio Deliverables

By the end of 12 weeks, the repository should contain:

```text
✓ Working Java application
✓ PostgreSQL database
✓ Flyway migrations
✓ REST APIs
✓ Automated tests
✓ Integration tests
✓ Docker setup
✓ Security
✓ Financial analysis engine
✓ Credit scoring
✓ Credit decisioning
✓ Authority matrix
✓ Maker-checker
✓ Audit
✓ AI assistant
✓ ADRs
✓ Architecture diagrams
✓ API documentation
✓ Demo dataset
✓ README
```

---

# 18. LinkedIn Content Plan

Do not post:

> "I built an AI loan application."

Post engineering problems.

## Post 1

### Why I chose a modular monolith for a credit platform

Discuss:

* deployment simplicity
* transactions
* domain boundaries
* future extraction

---

## Post 2

### Designing a financial calculation engine

Discuss:

```text
Raw Facts
    ↓
Derived Facts
    ↓
Ratios
    ↓
Risk Indicators
```

---

## Post 3

### Why AI should not make the final credit decision

Discuss:

```text
Deterministic rules
       ↓
Credit decision
       ↓
AI explanation
```

---

## Post 4

### Designing audit history for financial applications

Discuss:

```text
Business Audit
vs
Data Audit
```

---

## Post 5

### Preventing double disbursement

Discuss:

* idempotency
* transactions
* unique constraints
* locking

---

## Post 6

### When NOT to use microservices

Use your own project as the example.

---

# 19. Claude Code Operating Model

Use Claude in four modes.

## Mode 1 — Architect

```text
Analyze.
Challenge.
Design.
Do not code.
```

## Mode 2 — Developer

```text
Implement one bounded feature.
```

## Mode 3 — Reviewer

```text
Find problems.
Do not fix automatically.
```

## Mode 4 — Adversarial Tester

```text
Try to break it.
```

Never permanently operate in:

```text
"Build everything for me."
```

---

# 20. Recommended Claude Workflow

For every feature:

```text
Requirement
    ↓
Claude Analysis
    ↓
Your Review
    ↓
Implementation Plan
    ↓
Claude Implementation
    ↓
Tests
    ↓
Code Review
    ↓
Adversarial Review
    ↓
Fix One Finding
    ↓
Regression Tests
```

---

# 21. Git Workflow

Never allow Claude to commit automatically.

Use:

```text
feature/loan-application
feature/financial-analysis
feature/credit-scoring
feature/decision-engine
feature/audit
feature/ai-assistant
```

Commit manually after review.

Suggested commits:

```text
feat: add loan application lifecycle
feat: add underwriting attempts
feat: add financial analysis engine
feat: add financial ratios
feat: add credit scoring
feat: add credit decision engine
feat: add authority matrix
feat: add maker checker
feat: add offer and sanction
feat: add disbursement
feat: add audit framework
feat: add AI credit assistant
```

---

# 22. What NOT to Build

Do not waste the 12 weeks on:

```text
❌ Mobile application
❌ Fancy React dashboard
❌ Kubernetes
❌ 15 microservices
❌ Custom workflow engine
❌ Custom rules language
❌ Custom ORM
❌ Custom authentication system
❌ Generic AI agent framework
❌ Vector database
❌ RAG for everything
❌ Blockchain
❌ Chatbot UI
```

Those can be future extensions.

The core engineering matters more.

---

# 23. Possible Phase 2 After Week 12

Only after the core system is stable:

```text
Kafka
Redis
OpenTelemetry
Prometheus
Grafana
Cloud deployment
Object storage
Async document processing
OCR
External bureau integration
Event-driven architecture
Microservice extraction
```

Then demonstrate how the modular monolith can evolve.

For example:

```text
                    Modular Monolith
                          │
                 ┌────────┴────────┐
                 │                 │
          Financial Module     Decision Module
                 │                 │
                 └────────┬────────┘
                          │
                     Event Boundary
                          │
                    ┌─────┴─────┐
                    ▼           ▼
                 Kafka       External AI
```

The important interview question becomes:

> "Why did you start as a modular monolith, and what evidence would make you extract a service?"

That is a much stronger architectural discussion than simply saying:

> "I used microservices."

---

# 24. Final Success Criteria

At the end of 12 weeks, you should be able to explain:

### Architecture

```text
Why modular monolith?
Why these module boundaries?
Where are transactions?
What are the aggregates?
```

### Database

```text
Why PostgreSQL?
Why these indexes?
Which constraints protect business correctness?
```

### Financial

```text
How are derived values calculated?
How are ratios calculated?
How are missing values handled?
How is rounding handled?
```

### Credit

```text
How does scoring work?
How does rule evaluation work?
Why is the decision deterministic?
```

### Security

```text
How does maker-checker work?
How do you prevent approval bypass?
```

### Audit

```text
What changed?
Who changed it?
When?
Why?
What was the old value?
What was the new value?
```

### Concurrency

```text
What happens if two users approve simultaneously?
What happens if disbursement is retried?
```

### AI

```text
What can AI do?
What can AI NOT do?
How do you prevent AI from overriding credit policy?
```

### Scaling

```text
What changes at 10K applications?
What changes at 1M?
When would you introduce Kafka?
When would you introduce Redis?
When would you extract a microservice?
```

---

# 25. The Final Career Story

The project should allow you to tell this story:

> I built an open-source credit decisioning platform using Java 25, Spring Boot and PostgreSQL. I started with a modular monolith because the system required strong transactional consistency and the initial scale did not justify distributed services. The platform handles loan origination, underwriting, financial statement analysis, credit scoring, deterministic decisioning, authority-based approvals, offer/sanction, disbursement and financial audit.
>
> I deliberately separated deterministic credit decisions from AI. AI is used for explanation, anomaly detection and analyst assistance, while the actual decision remains explainable and policy-driven.
>
> I also designed the system for future evolution toward event-driven processing and selective microservice extraction.

That is the target.

**Do not optimize this project for number of features. Optimize it for depth of engineering decisions.**

---

# 26. One Master Claude Code Prompt

Keep this as the first prompt when starting the repository.

```text
You are the principal engineer helping me build an open-source Credit Decisioning Platform.

Technology:

- Java 25
- Spring Boot
- PostgreSQL
- Flyway
- JUnit 5
- Testcontainers
- Maven

Architecture:

- Modular monolith
- Domain-oriented modules
- REST APIs
- Strong transactional consistency

Business lifecycle:

Loan Application
→ KYC
→ Underwriting
→ Financial Analysis
→ Credit Scoring
→ Credit Decision
→ Offer
→ Sanction
→ Disbursement

Important requirements:

- One application can have multiple underwriting attempts.
- Successful underwriting can produce multiple offers.
- A selected offer can have multiple disbursements.
- Financial calculations must be deterministic.
- Derived values must be calculated before ratios.
- Credit decisions must be deterministic and explainable.
- AI must never make or override the final credit decision.
- Maker-checker must prevent self-approval.
- Financial and business data changes must be auditable.

Engineering rules:

- Accuracy > speed.
- Simplicity > cleverness.
- Verification > assumption.
- No speculative abstractions.
- No unnecessary interfaces.
- No God classes.
- No unnecessary private helper methods.
- No giant service implementations.
- No unnecessary design patterns.
- No microservices unless justified.
- No Kafka unless justified.
- No Redis unless justified.
- No generic framework-building.
- Use BigDecimal for monetary/financial calculations.
- Database constraints must protect important invariants.
- Avoid N+1 queries.
- Keep controllers thin.
- Keep transaction boundaries explicit.
- Tests are mandatory for important business logic.
- Never silently change business behavior.
- Never modify unrelated code.
- Never run git commit.

Claude workflow:

1. Inspect repository.
2. Understand existing code.
3. State assumptions.
4. Produce a short implementation plan.
5. Wait for approval when requirements are ambiguous.
6. Implement the smallest correct change.
7. Add/update tests.
8. Run relevant tests.
9. Review your own implementation.
10. Report exactly what changed.

For architecture questions:
Do not write code immediately.

For implementation requests:
Do not redesign unrelated areas.

For reviews:
Do not automatically fix findings.
Report findings first.

For financial calculations:
Trace raw data → derived facts → ratios → risk indicators.

For concurrency:
Analyze race conditions explicitly.

For AI:
Treat AI output as untrusted advisory information.

The goal is not maximum code.

The goal is a simple, correct, maintainable financial system that I can defend in a Senior Tech Lead / Solution Architect interview.
```

---

# End State

```text
             OPEN CREDIT PLATFORM
                     │
       ┌─────────────┴─────────────┐
       │                           │
   Loan Lifecycle            Credit Intelligence
       │                           │
       ├── Application             ├── Financial Analysis
       ├── KYC                     ├── Ratios
       ├── Underwriting            ├── Scoring
       ├── Offer                   ├── Rules
       ├── Sanction                └── Decision
       └── Disbursement
                     │
              ┌──────┴──────┐
              │             │
            Audit        AI Assistant
              │             │
              └──────┬──────┘
                     │
               Java 25
              Spring Boot
               PostgreSQL
```

**Primary goal:** build something you can explain deeply.

**Secondary goal:** publish it.

**Final goal:** use the project as evidence that you can operate at Senior Tech Lead / Architect level.
