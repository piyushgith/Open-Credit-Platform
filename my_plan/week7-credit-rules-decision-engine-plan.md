# Week 7 Implementation Plan — Credit Rules + Decision Engine

## Context

Week 6 (`my_plan/week6-credit-scoring-plan.md`) shipped the `scoring` module: `Score`
(`totalScore`, `riskGrade`) and `RiskFactor` rows, one set per `(FinancialAnalysisRun,
Scorecard)`. Per [my_docs/plan.md](../my_docs/plan.md) Week 7, this stage builds a deterministic
credit-decision engine — `CreditPolicy`, `CreditRule`, `RuleCondition`, `RuleResult`,
`CreditDecision`, `DecisionReason` — that consumes a `Score` plus the underlying `FinancialRatio`/
`RiskIndicator` rows and produces `APPROVE` / `REFER` / `DECLINE`, with passed/failed rules and a
required-authority hint. This is a **new module**, `decision`, sibling to `scoring`/`financial`/
`loan` — it only reads from `scoring` and `financial` via their existing repositories, the same
cross-module-repository pattern `ScoreService` already uses to read `financial`/`loan`.

## Repository facts this plan depends on

- `Score` has `totalScore` (int) and `riskGrade` (`RiskGrade` enum, `A/B/C/D/F`), keyed by
  `analysisRunId` + `scorecardId`. `FinancialRatioCode` has exactly 11 values; `RiskIndicatorCode`
  has exactly 6. All three are readable via `ScoreRepository`/`FinancialRatioRepository`/
  `RiskIndicatorRepository` given a `FinancialAnalysisRun.id`.
- `LoanApplication` already has its own `DecisionStatus` (`APPROVED/REFERRED/DECLINED`) and
  `status`/`decision` columns, driven by `LoanProcessingStrategy` (a simple FOIR check) at
  `submit()` time — this runs immediately at application submission, independent of financial
  statements/scoring, which are a separate, opt-in workflow nested under
  `/api/loans/{reference}/financial-statements/...` that never touches `LoanApplication.status`.
  Week 7's `CreditDecision` follows that same precedent: it is a read-only decisioning result
  nested the same way, and does **not** write back to `LoanApplication` — wiring a `CreditDecision`
  outcome into the application state machine is deferred (see Out of scope).
- Controller convention: responses wrapped in `ApiResponse<T>`; a resource nested one level
  deeper than its input reuses the parent's path segment plus its own id (see
  `.../analysis-runs/{analysisRunId}/score`).
- Last registered Liquibase changeset is `021`; Week 7 starts at `022`.

## Design decisions

| Decision | Choice |
|---|---|
| Rule shape ("no generic rule-engine framework") | Each `CreditRule` is **one threshold condition**: `(factorCode, operator, thresholdValue)` plus a `severity` (`HARD`/`SOFT`) and `ruleOrder`. No AND/OR trees, no expression language — the policy's rule *set* is implicitly ANDed (every configured rule is evaluated independently), matching the project's existing "band, not expression" precedent from `ScorecardRule`. |
| `RuleCondition` is not a persisted table | Mirrors `ScoreCalculator.Band`: a pure, transient record `(factorCode, operator, thresholdValue)` used only inside the stateless `DecisionEngine`. `CreditRule` stores the same three fields as flat columns — same reasoning `ScorecardRule` uses flat `minValue`/`maxValue` instead of an embeddable, since this codebase has no existing embeddable precedent and the roadmap's class list is illustrative, not a literal schema. |
| `DecisionReason` is not a persisted table either | It is the human-readable `description` already carried by each `RuleResult` row (mirrors `RiskFactor.description`) — persisting the same explanation twice under two names would be duplicate storage with no new query need. The API response surfaces it as `passedRules`/`failedRules` lists of `RuleResultResponse`, each carrying its `description`. |
| Rule inputs span two sources, so a new spanning enum is needed | `CreditRuleFactorCode` — distinct from `FinancialRatioCode`/`RiskIndicatorCode`/`RiskGrade`, exactly the precedent Week 6 set for `ScoreFactorCode` ("a new enum ... since it spans multiple sources"). Values: `TOTAL_SCORE`, `RISK_GRADE` (from `Score`); all 11 `FinancialRatioCode` names (from `FinancialRatio`, via the run); all 6 `RiskIndicatorCode` names (from `RiskIndicator`, via the run, encoded `1`/`0` — same encoding Week 6 used for `DECLINING_REVENUE`). `EMI_TO_INCOME` is deliberately excluded: it already feeds the score itself, so re-testing it in policy rules would be duplicate business logic against the same raw fact. |
| `RISK_GRADE` as a number | Ordinal encoding `A=5, B=4, C=3, D=2, F=1` (higher is better), so a rule like `RISK_GRADE >= 3` reads as "C or better." Documented on the enum/engine, same style as the `DECLINING_REVENUE` 1/0 encoding. |
| Comparison operator | New `ComparisonOperator` enum: `GTE, LTE, GT, LT, EQ, NEQ`, each with a pure `evaluate(BigDecimal actual, BigDecimal threshold)`. |
| Missing inputs | A rule whose factor can't be resolved for this run (ratio not persisted, e.g. zero-denominator) is skipped entirely — no `RuleResult` row, no effect on the outcome. Same "absence over defaulting" rule as Weeks 4-6. |
| Decision outcome | `outcome = DECLINE` if any **evaluated** `HARD` rule fails; else `REFER` if any evaluated `SOFT` rule fails; else `APPROVE` if at least one rule was evaluated and all passed; else (**zero** rules evaluable — e.g. every configured factor's data is missing) `REFER`, never `APPROVE` — a decision can't default to approval on no evidence ("accuracy > speed"). |
| `DecisionOutcome` is its own enum, not `loan.model.DecisionStatus` | `APPROVE/REFER/DECLINE` (present tense, matching the roadmap literally) in the new `decision` module — reusing `loan`'s `APPROVED/REFERRED/DECLINED` would both mismatch naming and create an unwanted `decision -> loan` enum dependency for a semantically different, later-stage decision. |
| Required authority (placeholder for Week 8) | Fixed, documented mapping on a new `RequiredAuthority` enum (`AUTO, CREDIT_OFFICER, SENIOR_CREDIT_MANAGER`) — **not** the real `AuthorityMatrix` (loan amount / branch / product), which is Week 8's job per the roadmap. `RequiredAuthority.forOutcome(outcome, riskGrade)`: `DECLINE` -> `AUTO`; `APPROVE` with grade `A`/`B` -> `AUTO`, grade `C`/`D`/`F` -> `CREDIT_OFFICER`; `REFER` -> `SENIOR_CREDIT_MANAGER`. Same "fixed enum, not a table" precedent as `RiskGrade`'s cut-offs. |
| One `CreditDecision` per `(score, policy)` | Unique constraint, same shape as `Score`'s `(analysis_run_id, scorecard_id)`. Re-deciding the same score under the same policy throws `DuplicateCreditDecisionException`; deciding under a newly-activated policy produces a new row. |
| "Only one active policy" enforcement | Same partial-unique-index mechanism as `scorecard.active`/`underwriting_attempt.active`: `CREATE UNIQUE INDEX uq_credit_policy_active ON credit_policy (active) WHERE active`, plus the admin service deactivating the previous active policy (flushed) before activating a new one. |
| One rule per `(policy, factorCode)` | A policy tests each factor at most once — enforced in `CreditPolicyAdminService` (`DuplicateCreditRuleFactorException`) rather than the DB, mirroring how `OverlappingScorecardRuleException` is a service-level check, not a Postgres exclusion constraint. |
| Deleting a policy | Blocked (`CreditPolicyInUseException`, 409) when active, or already referenced by a `CreditDecision` — identical guard to `ScorecardInUseException`. |
| Policy name uniqueness | `credit_policy.name` is `UNIQUE`; violating it throws `DuplicateCreditPolicyNameException` (409), mirroring `DuplicateScorecardNameException`. |
| Architecture | New module `decision`, sibling to `scoring`. One pure, stateless `@Component` (`DecisionEngine`, mirrors `ScoreCalculator`) does only the rule-matching/outcome logic — takes a lightweight `RuleCondition`-shaped input, not the `CreditRule` entity. `DecisionService` (transactional boundary) resolves inputs from `Score`/`FinancialRatio`/`RiskIndicator` directly (no `loan` module dependency at all, unlike `ScoreService`), loads the active `CreditPolicy` + rules, calls `DecisionEngine`, persists `CreditDecision`/`RuleResult`. `CreditPolicyAdminService` (separate from `DecisionService`) owns policy/rule CRUD, exactly mirroring `ScorecardAdminService`/`ScoreService`'s split. |

## Schema changes (Liquibase, additive changesets)

- `022-create-credit-policy.sql`: `credit_policy` — `id`, `name` (unique), `active BOOLEAN`,
  `created_at`, `version`; `CREATE UNIQUE INDEX uq_credit_policy_active ON credit_policy (active)
  WHERE active`.
- `023-create-credit-rule.sql`: `credit_rule` — `id`, `policy_id` (FK), `factor_code`, `operator`,
  `threshold_value NUMERIC(19,4)`, `severity`, `rule_order INT`, `description VARCHAR(200)`,
  `created_at`; unique `(policy_id, factor_code)`.
- `024-create-credit-decision.sql`: `credit_decision` — `id`, `score_id` (FK), `policy_id` (FK),
  `outcome`, `required_authority`, `created_at`; unique `(score_id, policy_id)`.
- `025-create-rule-result.sql`: `rule_result` — `id`, `decision_id` (FK), `factor_code`,
  `operator`, `threshold_value NUMERIC(19,4)`, `actual_value NUMERIC(19,4)`, `severity`,
  `passed BOOLEAN`, `description VARCHAR(200)`, `created_at`; unique `(decision_id, factor_code)`.
- `026-seed-default-credit-policy.sql`: one seeded `credit_policy` (`STANDARD_CREDIT_POLICY_V1`,
  `active=true`) plus rules mirroring the roadmap's example (`TOTAL_SCORE >= 700`,
  `DSCR >= 1.5`, `DEBT_TO_EBITDA <= 4.0`, all `HARD`) plus two `SOFT` rules
  (`INTEREST_COVERAGE >= 1.5`, `CURRENT_RATIO >= 1.0`) — a starting default, editable afterward
  via the new admin API, same role `021` plays for scorecards.

## Module: new `decision` module

- `model`: `ComparisonOperator` (`GTE, LTE, GT, LT, EQ, NEQ`, each with `evaluate(BigDecimal,
  BigDecimal)`), `RuleSeverity` (`HARD, SOFT`), `CreditRuleFactorCode` (18 values: `TOTAL_SCORE`,
  `RISK_GRADE`, the 11 `FinancialRatioCode` names, the 6 `RiskIndicatorCode` names),
  `DecisionOutcome` (`APPROVE, REFER, DECLINE`), `RequiredAuthority` (with
  `forOutcome(DecisionOutcome, RiskGrade)`), `CreditPolicy`, `CreditRule`, `CreditDecision`,
  `RuleResult` entities.
- `repository`: `CreditPolicyRepository` (`findByActiveTrue`, `existsByName`,
  `existsByNameAndIdNot`, `findAllByOrderByCreatedAtAsc`), `CreditRuleRepository`
  (`findAllByPolicyIdOrderByRuleOrderAsc`, `findAllByPolicyIdAndFactorCode`),
  `CreditDecisionRepository` (`findAllByScoreId`, `existsByScoreIdAndPolicyId`,
  `existsByPolicyId`), `RuleResultRepository` (`findAllByDecisionId`).
- `support.DecisionEngine`: pure `@Component`; `record RuleCondition(CreditRuleFactorCode
  factorCode, ComparisonOperator operator, BigDecimal thresholdValue, RuleSeverity severity)`;
  `record RuleOutcome(CreditRuleFactorCode factorCode, ComparisonOperator operator, BigDecimal
  thresholdValue, BigDecimal actualValue, RuleSeverity severity, boolean passed, String
  description)`; `record DecisionResult(DecisionOutcome outcome, RequiredAuthority
  requiredAuthority, List<RuleOutcome> ruleOutcomes)`; `evaluate(Map<CreditRuleFactorCode,
  BigDecimal> inputs, List<RuleCondition> rules, RiskGrade riskGrade)`.
- `dto`: `CreditDecisionResponse` (`id, scoreId, policyName, outcome, requiredAuthority,
  passedRules, failedRules, createdAt`), `RuleResultResponse` (`factorCode, operator,
  thresholdValue, actualValue, severity, description`), `CreditPolicyRequest`/`Response`,
  `CreditRuleRequest`/`Response` — same shape as the `scoring` module's scorecard-admin DTOs.
- `exception`: `NoActiveCreditPolicyException`, `DuplicateCreditDecisionException`,
  `CreditPolicyNotFoundException`, `DuplicateCreditPolicyNameException`,
  `CreditRuleNotFoundException`, `DuplicateCreditRuleFactorException`,
  `CreditPolicyInUseException` — no standalone `CreditDecisionNotFoundException`/get-by-id
  endpoint, mirroring `ScoringController`'s POST-decides/GET-lists shape exactly (no per-id get).
- `DecisionService` (`@Service @Transactional`): `decide(reference, statementId, analysisRunId,
  scoreId)` — loads the `Score` (validated against the run/statement/reference chain, same style
  as `ScoreService.getStatement`), the active `CreditPolicy` + its rules, resolves every available
  `CreditRuleFactorCode` input from that `Score` plus `FinancialRatioRepository`/
  `RiskIndicatorRepository` reads on the run, calls `DecisionEngine`, persists `CreditDecision` +
  `RuleResult` rows, returns `CreditDecisionResponse`. Also `list(reference, statementId,
  analysisRunId, scoreId)`.
- `DecisionController`: `@RequestMapping("/api/loans/{reference}/financial-statements/
  {statementId}/analysis-runs/{analysisRunId}/score/{scoreId}/decision")` — `POST` decides,
  `GET` lists.
- `CreditPolicyAdminService`/`CreditPolicyAdminController` (`/api/credit-policies`): full CRUD +
  activate for `CreditPolicy`, add/update/delete for `CreditRule`, mirroring
  `ScorecardAdminService`/`ScorecardAdminController` exactly.
- `GlobalExceptionHandler`: one `@ExceptionHandler` per new exception, following the existing
  status-code conventions (`404` not-found, `409` conflict/duplicate/in-use).

## Tests

- `DecisionEngineTest` (pure) — a `HARD` rule failing declines regardless of other passes; a
  `SOFT` rule failing (with all `HARD` rules passing) refers; all rules passing approves; a
  missing input skips only that rule; zero evaluable rules refers (not approves);
  `RequiredAuthority` mapping for every `(outcome, riskGrade)` combination named above;
  `RISK_GRADE` ordinal comparison at each boundary; deterministic repeatability.
- `DecisionIntegrationTest` (new, `@SpringBootTest` MockMvc style, mirrors
  `ScoringIntegrationTest`) — score a statement, then `POST .../decision` returns a persisted
  decision under the seeded `STANDARD_CREDIT_POLICY_V1`; strong financials approve; deciding the
  same score twice under the same policy returns `DuplicateCreditDecisionException`/409; deciding
  with no active policy returns `NoActiveCreditPolicyException`/409.
- `CreditPolicyAdminIntegrationTest` (new, mirrors `ScorecardAdminIntegrationTest`) —
  create/update/delete a policy; activating one deactivates the previous; a duplicate
  `factorCode` rule on the same policy is rejected; duplicate policy name is rejected; deleting an
  active or already-used policy is rejected.

## Out of scope (later weeks)

Wiring `CreditDecision.outcome` back into `LoanApplication.status`/`decision` (would require
reconciling this decision with the existing FOIR-based `LoanProcessingStrategy` decision made at
submission time — a design question for whichever week formally merges the two decisioning
paths). The real `AuthorityMatrix`/maker-checker workflow (Week 8), which will supersede this
week's fixed `RequiredAuthority` mapping with a configurable, loan-amount/branch/product-aware
matrix. Multiple simultaneously-active policies per product/segment. Policy versioning/effective
dating beyond a single `active` flag.
