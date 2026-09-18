# Backend Implementation Plan — Polymorphic Loan Origination API

## Context

`Open-Credit-Platform/` is currently a bare Spring Boot 4.1.1 / Java 25 skeleton: one `@SpringBootApplication` class, one empty-ish `application.properties`, one `contextLoads` test. Nothing else exists.

The goal of this first cut is a working vertical slice of the loan-origination entry point described in [my_docs/plan.md](my_docs/plan.md): a **single** `POST /api/loans/apply` endpoint that accepts different loan product payloads, routes each to product-specific business logic without `if/else` chains, persists the application to local PostgreSQL, and returns every response — success or failure — inside one standardized envelope.

This establishes four foundations the rest of the 12-week roadmap builds on:

1. **Jackson polymorphic (de)serialization** — one endpoint, many request/response shapes, keyed by a `productType` discriminator.
2. **Strategy pattern** — `Map<ProductType, LoanProcessingStrategy>` built by Spring at startup, O(1) routing. Adding a product touches no controller and no service.
3. **Generic response envelope** — `ApiResponse<T>` so every client parses one shape.
4. **Global exception handling** — `@RestControllerAdvice` guarantees the envelope holds on the error path too.

Decisions confirmed with you:

| Decision | Choice |
|---|---|
| Package root | `com.opencredit.platform` (repackage from `org.open.credit.app`) |
| Persistence | Single `loan_application` table + `jsonb` columns for product-specific data |
| Migrations | **Liquibase**; remove Flyway starters from the pom |
| Error shape | 7-field `ApiError` record + `ApiResponse.failure(...)` factories |
| DB config | Env vars with local defaults (`DB_URL`, `DB_USER`, `DB_PASSWORD`) |
| Products | Personal + Vehicle |

Out of scope for this cut (deferred, roadmap owns them): security/JWT, audit tables, maker-checker, the full `DRAFT→SUBMITTED→…→DISBURSED` lifecycle, idempotency keys, AI. The entity carries a `status` column so the lifecycle can land on top without a rewrite.

---

## Stage 0 — Project hygiene

Working directory for all Maven commands: `Open-Credit-Platform/`.

1. **Copy this plan** to `my_plan/backend-implementation-plan.md` (create the folder; it does not exist yet — only `my_docs/` does).
2. **Fix [pom.xml](Open-Credit-Platform/pom.xml).** Today it declares *both* `spring-boot-starter-flyway` and `spring-boot-starter-liquibase`; both would try to own the schema at startup. Remove:
   - `spring-boot-starter-flyway`
   - `spring-boot-starter-flyway-test`
   - `flyway-database-postgresql`

   Add:
   - `spring-boot-starter-validation` — Boot's web starter has not pulled in Bean Validation since 2.3, and `@Valid @RequestBody` is central to this design.

   Also fill the empty `<name>`/`<description>` and delete the placeholder empty `<licenses>/<developers>/<scm>` blocks (they are invalid-ish noise from the generator).
3. **Repackage** `org.open.credit.app` → `com.opencredit.platform`:
   - Move `OpenCreditPlatformApplication.java` to `src/main/java/com/opencredit/platform/`.
   - Move `OpenCreditPlatformApplicationTests.java` to the mirrored test path.
   - Delete the now-empty `org/` trees under main and test.
4. **`src/main/resources/application.properties`:**
   ```properties
   spring.application.name=Open-Credit-Platform

   spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/open_credit}
   spring.datasource.username=${DB_USER:postgres}
   spring.datasource.password=${DB_PASSWORD:postgres}

   spring.jpa.hibernate.ddl-auto=validate
   spring.jpa.open-in-view=false

   spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml

   spring.jackson.serialization.write-dates-as-timestamps=false
   spring.jackson.default-property-inclusion=non_null
   ```
   `ddl-auto=validate` makes an entity/schema drift fail at boot instead of silently at runtime. `open-in-view=false` per plan.md's N+1 stance.
5. **Create the database** (one time, from psql or pgAdmin):
   ```sql
   CREATE DATABASE open_credit;
   ```
   `psql` is not on PATH in this shell; run it from pgAdmin or the Postgres `bin/` directory.

**Checkpoint:** `./mvnw -q clean test` passes (`contextLoads` now boots against the real DB).

---

## Stage 1 — Liquibase schema

Files under `src/main/resources/db/changelog/`:

- `db.changelog-master.yaml` — includes the change files in order.
- `changes/001-create-loan-application.sql` — Liquibase formatted SQL (`--liquibase formatted sql`, `--changeset piyushprasad:001`).

```sql
CREATE SEQUENCE loan_reference_seq START 1 INCREMENT 1;

CREATE TABLE loan_application (
    id                 UUID           PRIMARY KEY,
    reference_number   VARCHAR(32)    NOT NULL UNIQUE,
    product_type       VARCHAR(32)    NOT NULL,
    applicant_name     VARCHAR(160)   NOT NULL,
    requested_amount   NUMERIC(19,2)  NOT NULL CHECK (requested_amount > 0),
    tenure_months      INTEGER        NOT NULL CHECK (tenure_months BETWEEN 1 AND 360),
    status             VARCHAR(32)    NOT NULL,
    decision           VARCHAR(32)    NOT NULL,
    request_details    JSONB          NOT NULL,
    decision_details   JSONB          NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version            BIGINT         NOT NULL DEFAULT 0
);

CREATE INDEX idx_loan_application_product_type ON loan_application (product_type);
CREATE INDEX idx_loan_application_created_at   ON loan_application (created_at DESC);
```

Why this shape: the common, queryable facts (amount, tenure, product, decision) are real typed columns with DB-level `CHECK` constraints — plan.md principle 15 says constraints are part of business correctness. Everything product-specific lives in `request_details` / `decision_details` JSONB, so a third product needs **zero** migrations. `version` is there for optimistic locking when the lifecycle/maker-checker work lands.

---

## Stage 2 — Response envelope

`com/opencredit/platform/common/api/`

**`ApiError.java`** — your 7-field record, unchanged:
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String error, String code,
                       String message, String path, Map<String, String> fieldErrors) {
    public static ApiError of(HttpStatus s, String code, String message, String path) { … }
    public ApiError withFieldErrors(Map<String, String> fieldErrors) { … }
}
```

**`ApiResponse.java`** — your class, with the reconciliation we agreed:
- Keep `status`, `message`, `timestamp`, `data`, `errors`, `@JsonInclude(NON_NULL)`, `@Builder`.
- Keep `success(T)` and `success(String, T)`.
- **Replace** `error(String, String errorCode)` and `error(String, List<ApiError>)` with `failure(ApiError)` and `failure(String message, List<ApiError>)`. Your sample handler already calls `ApiResponse.failure(error)`, which did not exist; the dropped overloads assumed a 2-field `ApiError(code, message)` that no longer exists.
- Change `timestamp` from `LocalDateTime` to `Instant` so it matches `ApiError.timestamp` — otherwise one payload carries two different time formats (`2026-09-19T10:15:30` vs `2026-09-19T10:15:30Z`).

---

## Stage 3 — Polymorphic DTOs

`com/opencredit/platform/loan/dto/`

**`ProductType`** enum (in `loan/`): `PERSONAL`, `VEHICLE`.

**`LoanRequest`** (abstract base):
```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.EXISTING_PROPERTY,
              property = "productType",
              visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PersonalLoanRequest.class, name = "PERSONAL"),
    @JsonSubTypes.Type(value = VehicleLoanRequest.class,  name = "VEHICLE")
})
public abstract class LoanRequest {
    @NotBlank private String applicantName;
    @NotNull @DecimalMin("10000.00") private BigDecimal requestedAmount;
    @NotNull @Min(6) @Max(360) private Integer tenureMonths;

    public abstract ProductType getProductType();
}
```

Two deliberate refinements over the spec you gave:

- **`include = As.EXISTING_PROPERTY` + abstract `getProductType()`** instead of the default `As.PROPERTY`. With the default, Jackson *consumes* the discriminator during deserialization and the object has no way to report its own type — but `LoanController` needs `request.getProductType()` to pick a strategy. Making the getter abstract and returning a hardcoded constant per subclass means the discriminator and the concrete class **cannot** disagree, and `EXISTING_PROPERTY` stops Jackson writing the field twice on the way out.
- Common validation lives on the base so every product inherits it.

**Subclasses** (`@EqualsAndHashCode(callSuper = true)` is required alongside Lombok `@Data` here):

| | Extra fields |
|---|---|
| `PersonalLoanRequest` | `@NotNull @Positive BigDecimal monthlyIncome`, `@NotNull BigDecimal existingEmi`, `@NotNull EmploymentType employmentType` (`SALARIED`, `SELF_EMPLOYED`) |
| `VehicleLoanRequest` | `@NotBlank String vehicleMake`, `@NotNull @Positive BigDecimal vehiclePrice`, `@NotNull BigDecimal downPayment`, `@NotNull VehicleCondition condition` (`NEW`, `USED`) |

**`LoanResponse`** — same `@JsonTypeInfo`/`@JsonSubTypes` treatment so the envelope's `data` field serializes with its own discriminator. Base fields: `applicationReference`, `decision` (`DecisionStatus`), `approvedAmount`, `interestRate`, `monthlyEmi`, `tenureMonths`, `List<String> reasons`. Subclasses add `foir` (personal) and `loanToValue` / `downPayment` (vehicle).

---

## Stage 4 — Persistence

`com/opencredit/platform/loan/model/` and `loan/repository/`

**`LoanApplication`** `@Entity`:
- `@Id UUID id` (assigned in Java via `UUID.randomUUID()`, not DB-generated).
- Scalar columns mirroring Stage 1.
- `@Enumerated(EnumType.STRING)` for `productType`, `status`, `decision`.
- JSONB columns mapped with Hibernate's native JSON support — **no `hypersistence-utils` dependency needed** on Hibernate 7:
  ```java
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> requestDetails;
  ```
- `@Version private long version;`

**`LoanApplicationRepository extends JpaRepository<LoanApplication, UUID>`** with `Optional<LoanApplication> findByReferenceNumber(String ref)`.

**Important:** do **not** hand the polymorphic DTO straight to Hibernate as a JSON-mapped POJO — Hibernate's internal serializer will not honour `@JsonTypeInfo`, so the stored JSON would lose its discriminator. Convert explicitly in the service with the injected Spring `ObjectMapper`:
```java
Map<String, Object> details = objectMapper.convertValue(request, new TypeReference<>() {});
```
This round-trips through the same Jackson config as the HTTP layer, so the persisted JSON is byte-for-byte what the client sent.

**Reference number:** `LN-` + zero-padded `nextval('loan_reference_seq')`, e.g. `LN-00000042`, via a small `@Query(nativeQuery = true)` on the repository.

---

## Stage 5 — Strategy pattern

`com/opencredit/platform/loan/strategy/`

```java
public interface LoanProcessingStrategy {
    ProductType getProductType();
    LoanResponse processLoan(LoanRequest request);
}
```

**`LoanServiceContext`** — the registry, built once at startup:
```java
@Component
public class LoanServiceContext {
    private final Map<ProductType, LoanProcessingStrategy> strategies;

    public LoanServiceContext(List<LoanProcessingStrategy> strategies) {
        this.strategies = strategies.stream().collect(
            Collectors.toUnmodifiableMap(LoanProcessingStrategy::getProductType, Function.identity()));
    }

    public LoanProcessingStrategy getStrategy(ProductType type) {
        LoanProcessingStrategy s = strategies.get(type);
        if (s == null) throw new UnsupportedProductTypeException(type);
        return s;
    }
}
```
`toUnmodifiableMap` throws on duplicate keys — two beans claiming `PERSONAL` fails the context at startup rather than silently shadowing one.

**`EmiCalculator`** (`loan/support/`) — one shared, tested method, so the two strategies do not duplicate the amortisation formula:
```
EMI = P · r · (1+r)^n / ((1+r)^n − 1),  r = annualRate / 12 / 100
```
`BigDecimal` throughout; rate math at scale 10, money rounded to scale 2 `HALF_UP`, documented in the Javadoc. `(1+r)^n` uses `BigDecimal.pow(n)`. Guard `r == 0`.

**`PersonalLoanStrategy`** — casts to `PersonalLoanRequest`, then:
- rate: `SALARIED` 11.5%, `SELF_EMPLOYED` 13.5%
- EMI via `EmiCalculator`
- FOIR = `(existingEmi + emi) / monthlyIncome`
- `APPROVED` ≤ 0.50, `REFERRED` ≤ 0.60, else `DECLINED`; every branch appends a human-readable reason.

**`VehicleLoanStrategy`** — casts to `VehicleLoanRequest`, then:
- rate: `NEW` 9.5%, `USED` 12.5%
- LTV = `requestedAmount / vehiclePrice`; max 0.85 `NEW`, 0.70 `USED`
- over-LTV ⇒ approved amount capped at `maxLtv × vehiclePrice` and `REFERRED`; `downPayment + requestedAmount < vehiclePrice` ⇒ `DECLINED`.

Strategies are **pure**: they compute and return, they never touch the repository. Persistence stays in the service (Stage 6) so the decision logic is unit-testable with no Spring context and no DB.

> Note: [my_docs/plan.md](my_docs/plan.md) §6 says not to create `strategy/` packages reflexively. Product-type routing is the concrete requirement that justifies it here — it is the pattern you explicitly asked for, and the alternative is the `if/else` chain that grows with every product.

---

## Stage 6 — Service and controller

**`LoanApplicationService`** (`@Service`, `@Transactional`):
```
getStrategy(request.getProductType())
  → strategy.processLoan(request)        // pure decision
  → allocate reference number
  → build + save LoanApplication         // request + decision as JSONB
  → return response with the reference set
```
Plus `findByReference(String)` → throws `LoanApplicationNotFoundException`.

**`LoanController`** (`@RestController`, `@RequestMapping("/api/loans")`) — thin, no logic:

| Method | Path | Status | Body |
|---|---|---|---|
| `POST` | `/apply` | `201 Created` + `Location: /api/loans/{ref}` | `ApiResponse<LoanResponse>` |
| `GET` | `/{reference}` | `200 OK` | `ApiResponse<LoanResponse>` |

`@Valid @RequestBody LoanRequest request` — Jackson picks the subclass, Bean Validation then validates the *subclass's* constraints. `201` is honest because a row really is created; the `GET` makes the `Location` header resolvable.

---

## Stage 7 — Global exception handler

`common/api/GlobalExceptionHandler` (`@RestControllerAdvice`). Each handler builds an `ApiError` via `ApiError.of(status, code, message, request.getRequestURI())` and returns `ApiResponse.failure(...)`.

| Exception | Status | `code` |
|---|---|---|
| `MethodArgumentNotValidException` | 422 | `VALIDATION_ERROR` (+ `fieldErrors` map, exactly as in your sample) |
| `HttpMessageNotReadableException` | 400 | `UNSUPPORTED_PRODUCT_TYPE` or `MALFORMED_REQUEST` |
| `UnsupportedProductTypeException` | 400 | `UNSUPPORTED_PRODUCT_TYPE` |
| `LoanApplicationNotFoundException` | 404 | `LOAN_APPLICATION_NOT_FOUND` |
| `Exception` (fallback) | 500 | `INTERNAL_ERROR` |

**The subtlety worth knowing:** an unknown `"productType": "HOME"` does **not** reach `LoanServiceContext` — Jackson fails first with `InvalidTypeIdException`, which Spring wraps in `HttpMessageNotReadableException`. So the handler must unwrap the cause to tell "unknown product type" apart from "malformed JSON" and emit a useful message listing the supported values. `UnsupportedProductTypeException` is the *second* line of defence: it only fires if a DTO subtype is registered but its strategy bean is missing — a wiring gap, not a client error, so it also gets logged at WARN.

Two departures from your sketch, both deliberate: a dedicated `UnsupportedProductTypeException` rather than `IllegalArgumentException` (blanket-catching `IllegalArgumentException` would swallow unrelated bugs as 400s), and the 500 fallback logs the stack trace but returns a generic message — never the exception text, which can leak schema details.

---

## Stage 8 — Tests

| Test | Type | Covers |
|---|---|---|
| `LoanRequestJsonTest` | `@JsonTest` | Both subtypes deserialize from the discriminator; responses serialize *with* it; unknown `productType` raises `InvalidTypeIdException`; discriminator appears exactly once |
| `EmiCalculatorTest` | plain JUnit | Known-value amortisation, zero rate, 1-month tenure, rounding to scale 2, no `ArithmeticException` on non-terminating division |
| `PersonalLoanStrategyTest` | plain JUnit | FOIR boundaries at 0.50 / 0.60 exactly, both employment types, reasons populated |
| `VehicleLoanStrategyTest` | plain JUnit | LTV cap NEW vs USED, approved-amount capping, insufficient down payment |
| `LoanServiceContextTest` | plain JUnit | Map built from a `List`, both types resolve, duplicate-key registration fails |
| `LoanControllerTest` | `@WebMvcTest` + `@MockitoBean` service | 201 + envelope shape; 422 with `fieldErrors`; 400 on `"HOME"`; 400 on malformed JSON; 404 |
| `LoanApplicationIntegrationTest` | `@SpringBootTest` + `MockMvc` | Full path against your local Postgres: apply → row exists → JSONB holds `productType` → `GET /{ref}` returns the same decision |

`@Min`/`@Max` boundary values and BigDecimal equality assertions use `compareTo`, not `equals` (scale differences).

The integration test needs `open_credit` running locally. Testcontainers (which [my_docs/plan.md](my_docs/plan.md) §26 lists) is the better long-term answer for CI — noted as a follow-up, not added now, since no Testcontainers dependency is in the pom.

---

## Verification

```bash
cd Open-Credit-Platform
./mvnw clean test          # all unit + integration tests
./mvnw spring-boot:run     # boots on :8080, Liquibase applies 001
```

Success — personal:
```bash
curl -i -X POST http://localhost:8080/api/loans/apply \
  -H 'Content-Type: application/json' \
  -d '{"productType":"PERSONAL","applicantName":"Piyush Prasad",
       "requestedAmount":500000.00,"tenureMonths":60,
       "monthlyIncome":120000.00,"existingEmi":15000.00,
       "employmentType":"SALARIED"}'
```
Expect `201`, a `Location` header, `"status":"SUCCESS"`, `data.productType == "PERSONAL"`, a `foir` field, and **no** `errors` key.

Success — vehicle: same URL, `{"productType":"VEHICLE", …,"vehicleMake":"Honda","vehiclePrice":1200000.00,"downPayment":200000.00,"condition":"NEW"}`. Expect `data.loanToValue` present and `foir` absent — proof the polymorphic response works.

Unknown product → `400`, `code: UNSUPPORTED_PRODUCT_TYPE`:
```bash
curl -i -X POST http://localhost:8080/api/loans/apply \
  -H 'Content-Type: application/json' \
  -d '{"productType":"HOME","applicantName":"X","requestedAmount":100000,"tenureMonths":12}'
```

Validation → `422` with a `fieldErrors` map, and **no** `data` key:
```bash
curl -i -X POST http://localhost:8080/api/loans/apply \
  -H 'Content-Type: application/json' \
  -d '{"productType":"PERSONAL","applicantName":"","requestedAmount":-5,"tenureMonths":2}'
```

Round-trip: `curl http://localhost:8080/api/loans/LN-00000001` → `200`, same decision.

Persistence:
```sql
SELECT reference_number, product_type, decision, request_details FROM loan_application;
```
`request_details` must contain `"productType": "PERSONAL"` — that confirms the `ObjectMapper.convertValue` path preserved the discriminator.

**The open-closed check:** adding a `HomeLoanRequest/Response` + `HomeLoanStrategy` and one `@JsonSubTypes.Type` line must require **no** change to `LoanController`, `LoanApplicationService`, `LoanServiceContext`, or the schema. If it does, the routing design is wrong.

---

## Files

**Modified:** `Open-Credit-Platform/pom.xml`, `src/main/resources/application.properties`, `OpenCreditPlatformApplication.java` (moved), `OpenCreditPlatformApplicationTests.java` (moved)

**New** — under `src/main/java/com/opencredit/platform/`:
```
common/api/       ApiResponse, ApiError, GlobalExceptionHandler
loan/             ProductType, LoanController, LoanApplicationService, LoanServiceContext
loan/dto/         LoanRequest, LoanResponse, Personal*/Vehicle* (4), EmploymentType, VehicleCondition
loan/strategy/    LoanProcessingStrategy, PersonalLoanStrategy, VehicleLoanStrategy
loan/support/     EmiCalculator
loan/model/       LoanApplication, DecisionStatus, ApplicationStatus
loan/repository/  LoanApplicationRepository
loan/exception/   UnsupportedProductTypeException, LoanApplicationNotFoundException
```
plus `src/main/resources/db/changelog/` (2 files), 7 test classes, and `my_plan/backend-implementation-plan.md`.
