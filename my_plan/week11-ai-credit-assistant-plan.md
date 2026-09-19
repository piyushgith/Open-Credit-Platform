# Week 11 — AI Credit Assistant

## Context

By Week 7, `CreditDecisionResponse` (`decision/dto/CreditDecisionResponse.java`) already carries
`passedRules`/`failedRules` as lists of `RuleResultResponse`, each with a human-readable `description`
string built by `DecisionEngine.describe(...)`. `ScoreResponse` carries `contributingFactors`/
`failedFactors` the same way. `FinancialRatioResponse`/`RiskIndicatorResponse` carry the computed ratios
and triggered risk flags. In other words, the exact "structured facts rather than unrestricted database
access" `my_docs/plan.md` asks the AI module to receive already exist, as the response DTOs three earlier
weeks built for the REST API — nothing new needs to be invented to assemble them, just wired to a new
consumer.

`pom.xml` already has `spring-boot-starter-webclient`. No AI/LLM dependency exists yet. Given the project
is single-provider (this is explicitly a Claude Code build) and WebClient is already present, the plan is
a thin `WebClient`-based client calling the Anthropic Messages API directly — not `spring-ai`, which would
pull in a provider-abstraction layer this single-provider project has no use for (principle #8: no
generic framework-building).

## Architectural boundary (the part `my_docs/plan.md` calls "important")

The new `ai` module takes a compile-time dependency on `decision`, `scoring`, and `financial`'s **response
DTOs only** — `CreditDecisionResponse`, `ScoreResponse`, `List<FinancialRatioResponse>`,
`List<RiskIndicatorResponse>` — and has zero dependency on any of their repositories or entities. This is
checkable by construction: `AiCreditAssistantService`'s constructor takes no `*Repository` from another
module, only the services that already know how to produce those DTOs
(`DecisionService.list`/`ScoreService`-equivalent lookups). Concretely, the guarantee "AI never
approves/rejects/modifies the score/overrides a rule/modifies financial facts/modifies audit history"
holds because the `ai` module never imports a single `*Repository` or `*Service.save`-capable type from
`decision`, `scoring`, `financial`, `loan`, `authority`, or (Week 10's) `audit` — it only reads their
already-built response objects. No code path exists that could write AI output back into `CreditDecision`,
`Score`, or `ApprovalCase`, because `AiCreditAssistantService` never touches those repositories at all.

## Scope: three task types, one context-assembly step

`my_docs/plan.md` lists six capabilities (financial-analysis explanation, anomaly identification,
risk-factor explanation, missing-information suggestions, credit-memo draft, suggested questions). Rather
than six bespoke endpoints (that *would* be over-engineering — six near-identical controller methods), or
one fully generic "ask the AI anything" endpoint (that's the "generic AI agent framework" §22 explicitly
rules out), this week implements one endpoint with a **fixed, closed enum** of three task types, sharing
one context-assembly step and one small prompt-template method per type:

```
AiTaskType: EXPLAIN_DECISION | SUGGEST_QUESTIONS | DRAFT_CREDIT_MEMO
```

- `EXPLAIN_DECISION` — covers risk-factor explanation + financial-analysis explanation: the prompt is
  built from `outcome`, `failedRules`/`passedRules` descriptions, and `contributingFactors`/
  `failedFactors`.
- `SUGGEST_QUESTIONS` — covers missing-information + suggested-questions-for-analyst: same context, asks
  for 3-5 follow-up questions an analyst should ask, weighted toward whatever failed.
- `DRAFT_CREDIT_MEMO` — same context, asks for a short structured memo (applicant, amount, outcome, key
  drivers).

**Anomaly identification is explicitly out of scope this week** — it needs a cross-period/cross-run trend
comparison that isn't assembled anywhere in the codebase yet (every existing DTO describes one analysis
run, not a series), and bolting on ad hoc trend logic just to hit six-for-six would be scope creep. Call
this out rather than fake it.

## Request flow

```
POST /api/ai/credit-decisions/{decisionId}/assist   { taskType }
   -> AiCreditAssistantService assembles context from DecisionService/ScoreService/FinancialStatementService
      response DTOs (read-only, same repositories those services already query)
   -> builds the task-specific prompt
   -> AiClient (WebClient) calls the Anthropic Messages API
   -> response persisted + returned, aiGenerated: true
```

## Failure handling (these map directly to the required test list)

- **AI unavailable** — connection failure or non-2xx from the provider -> caught, wrapped as
  `AiUnavailableException` -> new `GlobalExceptionHandler` handler returns `503` with code
  `AI_UNAVAILABLE`, same translate-infra-exception-to-`ApiError` pattern every other handler in that class
  already follows.
- **Timeout** — `WebClient` configured with an explicit response timeout (`ai.provider.timeout-ms` in
  `application.properties`, same `${ENV_VAR:default}` convention as `DB_URL`); a timeout is just another
  path into `AiUnavailableException`.
- **Malformed response** — provider returns 200 but the JSON doesn't match the expected shape -> caught
  separately as `AiResponseParseException` -> `503`, code `AI_RESPONSE_INVALID` (kept distinct from
  `AI_UNAVAILABLE` so "the provider is down" and "the provider sent garbage" are distinguishable in logs
  and in the API response).
- **Unsupported recommendation** — `taskType` outside the closed enum fails Jackson enum deserialization
  the same way every other bad-enum request body already does in this codebase — no new handling needed,
  `GlobalExceptionHandler`'s existing `HttpMessageNotReadableException`/`MethodArgumentNotValidException`
  handlers already cover it. Reusing that existing path (rather than writing a bespoke check) is the point.
- **Deterministic decision remains unchanged** — the strongest version of this test re-reads the
  `CreditDecision` row from `CreditDecisionRepository` before and after calling the assist endpoint and
  asserts it is byte-identical. This is possible today precisely because `AiCreditAssistantService` never
  holds a reference to that repository.

## Persistence

`AiAssistantInteraction` (new `ai` module): `id`, `decisionId`, `taskType`, `requestPrompt`,
`responseText`, `model` (e.g. `claude-sonnet-5`), `createdAt`, `requestId`/`correlationId` (Week 10's
request context — this module is built after Week 10, so it can rely on that filter already existing).
Gives a `GET` history endpoint for free and a natural `AI_ASSISTANCE_REQUESTED` business-audit event to
emit through Week 10's `AuditService`, tying the two weeks together rather than treating them as isolated.

## Endpoints

```
POST /api/ai/credit-decisions/{decisionId}/assist    body: { taskType }
GET  /api/ai/credit-decisions/{decisionId}/assist    prior AI interactions for this decision
```

## Migrations

```
039-create-ai-assistant-interaction.sql
```

## Config

```
ai.provider.api-key=${ANTHROPIC_API_KEY:}
ai.provider.base-url=${AI_BASE_URL:https://api.anthropic.com}
ai.provider.model=${AI_MODEL:claude-sonnet-5}
ai.provider.timeout-ms=${AI_TIMEOUT_MS:10000}
```

An empty `api-key` is treated as "AI unavailable" at call time (not a startup failure) — the rest of the
platform must keep working with zero AI configuration, since AI is explicitly not on the critical path.

## Tests

- `AiCreditAssistantServiceTest` — context assembly for each task type; provider failure ->
  `AiUnavailableException`; malformed provider response -> `AiResponseParseException`; timeout ->
  `AiUnavailableException`.
- Integration test — full `assist` call against a mocked provider (WireMock or a stub `WebClient`
  exchange function, not a real network call in CI), asserting `CreditDecision.outcome`/
  `requiredAuthority` are unchanged before/after.
- Enum-rejection test — invalid `taskType` in the request body -> `400`, reusing the existing exception
  handler (no new test infrastructure needed beyond an existing-pattern MockMvc call).
- `AiAssistantInteraction` persistence test — row written with correct `decisionId`/`taskType`/
  `requestId`/`correlationId`.

## Out of scope

Anomaly identification (needs cross-run trend data not assembled anywhere yet — see above). Any write
path from AI output back into `CreditDecision`, `Score`, `ApprovalCase`, or `LoanApplication` — not
"discouraged," structurally absent. Multi-provider abstraction (`spring-ai` or similar) — single provider,
direct `WebClient` call. Streaming responses. A generic "ask anything" prompt endpoint.
