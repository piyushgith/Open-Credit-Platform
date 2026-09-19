# Camunda 7 vs Flowable — Are They the Same?

## Short Answer

Yes. Camunda 7 uses an **identical persistence model** to Flowable. Same table names, same locking strategy, same restart recovery guarantees. That's not a coincidence — they share the same ancestor.

---

## Why They're Almost Identical

Flowable was **forked directly from Activiti** in 2016. Camunda 7 was also forked from Activiti in 2013. They share the same architectural DNA.

```
Activiti (original open source engine)
    │
    ├── Camunda (forked 2013) → Camunda 7
    └── Flowable (forked 2016) → Flowable
```

Same persistence model, same table naming convention (`ACT_*`), same async job executor pattern, same BPMN 2.0 execution semantics. The differences are at the edges — tooling, licensing, community support.

---

## Same Tables, Same Guarantees

| Camunda 7 | Flowable | Purpose |
|---|---|---|
| `ACT_RU_EXECUTION` | `ACT_RU_EXECUTION` | Current node per process instance |
| `ACT_RU_TASK` | `ACT_RU_TASK` | Open human tasks |
| `ACT_RU_JOB` | `ACT_RU_JOB` | Timers, async jobs, lock expiry |
| `ACT_RU_VARIABLE` | `ACT_RU_VARIABLE` | Runtime process variables |
| `ACT_HI_PROCINST` | `ACT_HI_PROCINST` | Completed process history |
| `ACT_HI_TASKINST` | `ACT_HI_TASKINST` | Completed task history |
| `ACT_HI_ACTINST` | `ACT_HI_ACTINST` | Every activity with timestamps |

Identical names, identical columns, identical locking strategy. A Camunda 7 migration to Flowable is often just a dependency swap.

---

## Where They Actually Differ

| Dimension | Camunda 7 | Flowable |
|---|---|---|
| License | Apache 2.0 (engine only) | Apache 2.0 |
| Cockpit / admin UI | ✅ Mature, polished | ✅ Good, less mature |
| REST API | ✅ Full, well documented | ✅ Full |
| Spring Boot starter | ✅ First class | ✅ First class |
| DMN (decision tables) | ✅ Built-in | ✅ Built-in |
| CMMN (case management) | ✅ | ✅ |
| Camunda 8 migration path | ✅ Official | ❌ Different product |
| Community size | Larger | Smaller |
| Enterprise support | ✅ | ✅ |
| Job executor clustering | Same | Same |

---

## The One Meaningful Difference — Camunda 7 vs Camunda 8

**Camunda 7 vs Camunda 8 is a bigger decision** than Camunda 7 vs Flowable.

Camunda 8 (Zeebe engine) is a **completely different architecture** — no shared DB tables, event-log based, Kafka-style. If your bank might migrate to Camunda 8 later, starting on Camunda 7 gives you a cleaner path. Flowable has no equivalent migration target.

```
Camunda 7 (Activiti-based, DB-centric)
    └── migration path → Camunda 8 (Zeebe, event-log, cloud-native)

Flowable
    └── no equivalent next-gen engine
```

---

## Code Difference — Almost Nothing

### Camunda 7

```xml
<dependency>
    <groupId>org.camunda.bpm.springboot</groupId>
    <artifactId>camunda-bpm-spring-boot-starter</artifactId>
    <version>7.21.0</version>
</dependency>
```

```java
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;

@Component
public class RiskScoringDelegate implements JavaDelegate {

    private final RiskEngine riskEngine;

    @Override
    public void execute(DelegateExecution execution) {
        String loanId  = (String)     execution.getVariable("applicationId");
        BigDecimal amt = (BigDecimal) execution.getVariable("loanAmount");

        RiskScore score = riskEngine.score(loanId, amt);

        execution.setVariable("riskScore",    score.getValue());
        execution.setVariable("riskCategory", score.getCategory().name());
    }
}
```

### Flowable

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>7.0.1</version>
</dependency>
```

```java
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

@Component
public class RiskScoringDelegate implements JavaDelegate {

    private final RiskEngine riskEngine;

    @Override
    public void execute(DelegateExecution execution) {
        String loanId  = (String)     execution.getVariable("applicationId");
        BigDecimal amt = (BigDecimal) execution.getVariable("loanAmount");

        RiskScore score = riskEngine.score(loanId, amt);

        execution.setVariable("riskScore",    score.getValue());
        execution.setVariable("riskCategory", score.getCategory().name());
    }
}
```

**The only difference is the import package.** The BPMN XML is 100% compatible between both engines.

---

## Restart & Recovery — Identical Behaviour

Both engines guarantee the same recovery on server restart:

| Scenario | Behaviour |
|---|---|
| Human task waiting in queue | Persisted in `ACT_RU_TASK` — no impact on restart |
| SLA timer counting down | Due timestamp in `ACT_RU_JOB` — fires on restart if overdue |
| Async service task in-flight | Lock expires on restart — job retried automatically |
| Cluster node crash | Other nodes acquire expired lock and re-execute |

### The One Risk — Non-Idempotent External Calls

If a delegate calls an external system (CBS, bureau, payment rail) and the JVM crashes before Flowable writes the result, the job will be **retried on restart**. You must guard against double execution:

```java
@Component
public class DisbursementDelegate implements JavaDelegate {

    private final CoreBankingService cbs;
    private final DisbursementLogRepository logRepo;

    @Override
    @Transactional
    public void execute(DelegateExecution execution) {
        String loanId = (String) execution.getVariable("applicationId");

        // Idempotency check — prevent double disbursement
        if (logRepo.existsByLoanId(loanId)) {
            String ref = logRepo.findByLoanId(loanId).getTransactionRef();
            execution.setVariable("disbursementRef", ref);
            return;
        }

        // Persist intent BEFORE calling CBS
        DisbursementLog log = logRepo.save(
            DisbursementLog.builder()
                .loanId(loanId)
                .status(DisbursementStatus.INITIATED)
                .initiatedAt(Instant.now())
                .build()
        );

        // Call external system
        DisbursementResult result = cbs.disburse(loanId);

        // Update log and set variable — same transaction
        log.setTransactionRef(result.getRef());
        log.setStatus(DisbursementStatus.COMPLETED);
        logRepo.save(log);

        execution.setVariable("disbursementRef", result.getRef());
    }
}
```

This pattern applies equally to **both Camunda 7 and Flowable**.

---

## application.yml — Camunda 7

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/loandb
    username: ${DB_USER}
    password: ${DB_PASS}

camunda.bpm:
  auto-deployment-enabled: true
  history-level: full
  job-executor-activate: true
  generic-properties:
    properties:
      jobExecutorCorePoolSize: 8
      jobExecutorMaxPoolSize: 50
      jobExecutorMaxJobsPerAcquisition: 10
      jobExecutorLockTimeInMillis: 300000   # 5 min — adjust to slowest task
```

## application.yml — Flowable

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/loandb
    username: ${DB_USER}
    password: ${DB_PASS}

flowable:
  async-executor-activate: true
  history-level: full
  database-schema-update: true
  async-executor-core-pool-size: 8
  async-executor-max-pool-size: 50
  async-executor-max-timer-jobs-per-acquisition: 10
  async-executor-lock-time-in-millis: 300000
```

---

## Camunda Cockpit — The Key Operational Advantage

Camunda 7 ships with **Cockpit** — a web UI that gives ops teams and business analysts live visibility into running processes without any custom tooling.

What Cockpit shows out of the box:
- All running process instances and their current node
- Open human tasks with assignees and SLA breach warnings
- Process variables at any point in time
- Incident list — failed jobs with stack traces
- Ability to reassign stuck tasks, retrigger failed jobs, cancel instances

This operational visibility matters significantly in **regulated environments** like banking where you need to answer "where is loan #12345 right now and who is holding it up?" without writing a single query.

---

## Recommendation for a Bank LOS

| Scenario | Recommendation |
|---|---|
| Existing team familiar with BPMN | Camunda 7 or Flowable — functionally equivalent |
| Planning cloud-native future | Start on Camunda 7, migrate to Camunda 8 (Zeebe) later |
| Need mature ops tooling for business teams | Camunda 7 — Cockpit is more mature |
| Greenfield, full open source control | Flowable |
| Enterprise SLA + vendor support contract | Camunda 7 Enterprise |

For most banks, **Camunda 7** is the safer choice — larger community, better Cockpit, and a clear migration path to Camunda 8 if the architecture evolves toward cloud-native event-driven processing.
