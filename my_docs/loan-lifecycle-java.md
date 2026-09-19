# Loan Lifecycle Management in Java

## Loan States

```
APPLICATION_SUBMITTED → UNDER_REVIEW → APPROVED → SANCTIONED → DISBURSED → ACTIVE → CLOSED
                                     ↘ REJECTED
                                                          ACTIVE → DELINQUENT → ACTIVE (cured)
                                                                              → NPA → WRITTEN_OFF
```

---

## State & Event Enums

```java
public enum LoanState {
    APPLICATION_SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    SANCTIONED,
    DISBURSED,
    ACTIVE,
    DELINQUENT,
    NPA,
    CLOSED,
    WRITTEN_OFF
}

public enum LoanEvent {
    SUBMIT_APPLICATION,
    START_UNDERWRITING,
    APPROVE,
    REJECT,
    SANCTION,
    DISBURSE,
    PAYMENT_RECEIVED,
    PAYMENT_MISSED,
    CURE,
    CLOSE,
    WRITE_OFF
}
```

---

## Option 1 — Spring State Machine

### Dependency

```xml
<dependency>
    <groupId>org.springframework.statemachine</groupId>
    <artifactId>spring-statemachine-core</artifactId>
    <version>4.0.0</version>
</dependency>
```

### Configuration

```java
@Configuration
@EnableStateMachineFactory
public class LoanStateMachineConfig
        extends StateMachineConfigurerAdapter<LoanState, LoanEvent> {

    @Override
    public void configure(StateMachineStateConfigurer<LoanState, LoanEvent> states)
            throws Exception {
        states.withStates()
            .initial(LoanState.APPLICATION_SUBMITTED)
            .states(EnumSet.allOf(LoanState.class))
            .end(LoanState.CLOSED)
            .end(LoanState.REJECTED)
            .end(LoanState.WRITTEN_OFF);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<LoanState, LoanEvent> transitions)
            throws Exception {
        transitions
            .withExternal()
                .source(LoanState.APPLICATION_SUBMITTED).target(LoanState.UNDER_REVIEW)
                .event(LoanEvent.START_UNDERWRITING).and()
            .withExternal()
                .source(LoanState.UNDER_REVIEW).target(LoanState.APPROVED)
                .event(LoanEvent.APPROVE)
                .guard(creditScoreGuard()).and()
            .withExternal()
                .source(LoanState.UNDER_REVIEW).target(LoanState.REJECTED)
                .event(LoanEvent.REJECT).and()
            .withExternal()
                .source(LoanState.APPROVED).target(LoanState.SANCTIONED)
                .event(LoanEvent.SANCTION).and()
            .withExternal()
                .source(LoanState.SANCTIONED).target(LoanState.DISBURSED)
                .event(LoanEvent.DISBURSE).and()
            .withExternal()
                .source(LoanState.DISBURSED).target(LoanState.ACTIVE)
                .event(LoanEvent.PAYMENT_RECEIVED).and()
            .withExternal()
                .source(LoanState.ACTIVE).target(LoanState.DELINQUENT)
                .event(LoanEvent.PAYMENT_MISSED).and()
            .withExternal()
                .source(LoanState.DELINQUENT).target(LoanState.ACTIVE)
                .event(LoanEvent.CURE).and()
            .withExternal()
                .source(LoanState.DELINQUENT).target(LoanState.NPA)
                .event(LoanEvent.WRITE_OFF)
                .guard(dpdGuard()).and()
            .withExternal()
                .source(LoanState.ACTIVE).target(LoanState.CLOSED)
                .event(LoanEvent.CLOSE).and()
            .withExternal()
                .source(LoanState.NPA).target(LoanState.WRITTEN_OFF)
                .event(LoanEvent.WRITE_OFF);
    }

    @Bean
    public Guard<LoanState, LoanEvent> creditScoreGuard() {
        return ctx -> {
            Integer score = (Integer) ctx.getExtendedState().getVariables().get("creditScore");
            return score != null && score >= 650;
        };
    }

    @Bean
    public Guard<LoanState, LoanEvent> dpdGuard() {
        return ctx -> {
            Integer dpd = (Integer) ctx.getExtendedState().getVariables().get("daysOverdue");
            return dpd != null && dpd >= 90;
        };
    }
}
```

### Lifecycle Service

```java
@Service
@Slf4j
public class LoanLifecycleService {

    private final StateMachineFactory<LoanState, LoanEvent> factory;
    private final LoanRepository loanRepository;
    private final LoanStateHistoryRepository historyRepository;

    public void sendEvent(String loanId, LoanEvent event, Map<String, Object> variables) {
        StateMachine<LoanState, LoanEvent> sm = buildAndRestoreSM(loanId);

        variables.forEach((k, v) ->
            sm.getExtendedState().getVariables().put(k, v));

        boolean accepted = sm.sendEvent(
            MessageBuilder.withPayload(event)
                          .setHeader("loanId", loanId)
                          .build()
        );

        if (!accepted) {
            throw new IllegalStateTransitionException(
                "Event " + event + " rejected for loan " + loanId
                + " in state " + sm.getState().getId()
            );
        }

        loanRepository.updateState(loanId, sm.getState().getId());
        historyRepository.save(new LoanStateHistory(loanId, event, sm.getState().getId()));
    }

    private StateMachine<LoanState, LoanEvent> buildAndRestoreSM(String loanId) {
        StateMachine<LoanState, LoanEvent> sm = factory.getStateMachine(loanId);
        sm.stop();
        sm.getStateMachineAccessor().doWithAllRegions(access -> {
            LoanState currentState = loanRepository.getCurrentState(loanId);
            access.resetStateMachine(
                new DefaultStateMachineContext<>(currentState, null, null, null)
            );
        });
        sm.start();
        return sm;
    }
}
```

---

## Option 2 — Temporal.io (Durable Workflows)

Best for long-running loan lifecycles spanning months or years. Auto-retries, human signals, and crash-safe execution built in.

### Dependency

```xml
<dependency>
    <groupId>io.temporal</groupId>
    <artifactId>temporal-sdk</artifactId>
    <version>1.24.1</version>
</dependency>
```

### Workflow & Activity Interfaces

```java
@WorkflowInterface
public interface LoanWorkflow {
    @WorkflowMethod
    LoanOutcome processLoan(LoanApplication application);

    @SignalMethod
    void sanction();

    @SignalMethod
    void receivePayment();

    @QueryMethod
    LoanState getCurrentState();
}

@ActivityInterface
public interface LoanActivities {
    CreditDecision runCreditCheck(String applicantId);
    void generateOffer(String loanId, BigDecimal amount);
    void disburseFunds(String loanId, String accountNumber);
    void sendNotification(String loanId, String message);
}
```

### Workflow Implementation

```java
public class LoanWorkflowImpl implements LoanWorkflow {

    private final LoanActivities activities = Workflow.newActivityStub(
        LoanActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(10))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build())
            .build()
    );

    private volatile boolean sanctionReceived = false;
    private volatile boolean paymentReceived = false;
    private volatile boolean loanClosed = false;
    private LoanState currentState = LoanState.APPLICATION_SUBMITTED;

    @Override
    public LoanOutcome processLoan(LoanApplication application) {
        currentState = LoanState.UNDER_REVIEW;
        CreditDecision decision = activities.runCreditCheck(application.getApplicantId());

        if (!decision.isApproved()) {
            currentState = LoanState.REJECTED;
            return LoanOutcome.rejected(decision.getReason());
        }

        currentState = LoanState.APPROVED;
        activities.generateOffer(application.getLoanId(), decision.getApprovedAmount());

        // Wait for human sanction — could be days
        Workflow.await(Duration.ofDays(30), () -> this.sanctionReceived);
        if (!this.sanctionReceived) {
            return LoanOutcome.expired();
        }

        currentState = LoanState.SANCTIONED;
        activities.disburseFunds(application.getLoanId(), application.getAccountNumber());
        currentState = LoanState.DISBURSED;

        // Repayment loop for entire loan tenure
        int missedPayments = 0;
        while (!loanClosed) {
            paymentReceived = false;
            Workflow.await(Duration.ofDays(30), () -> paymentReceived || loanClosed);

            if (paymentReceived) {
                missedPayments = 0;
                currentState = LoanState.ACTIVE;
            } else if (!loanClosed) {
                missedPayments++;
                currentState = LoanState.DELINQUENT;
                if (missedPayments >= 3) {
                    currentState = LoanState.NPA;
                    activities.sendNotification(application.getLoanId(), "NPA triggered");
                }
            }
        }

        return LoanOutcome.closed();
    }

    @Override public void sanction()        { this.sanctionReceived = true; }
    @Override public void receivePayment()  { this.paymentReceived = true; }
    @Override public LoanState getCurrentState() { return currentState; }
}
```

---

## Option 3 — Domain Events + Outbox Pattern

No framework dependency. Loan aggregate owns transition rules; domain events drive downstream side effects.

### Valid Transitions Map

```java
private static final Map<LoanState, Set<LoanState>> VALID_TRANSITIONS = Map.of(
    LoanState.APPLICATION_SUBMITTED, Set.of(LoanState.UNDER_REVIEW),
    LoanState.UNDER_REVIEW,          Set.of(LoanState.APPROVED, LoanState.REJECTED),
    LoanState.APPROVED,              Set.of(LoanState.SANCTIONED),
    LoanState.SANCTIONED,            Set.of(LoanState.DISBURSED),
    LoanState.DISBURSED,             Set.of(LoanState.ACTIVE),
    LoanState.ACTIVE,                Set.of(LoanState.DELINQUENT, LoanState.CLOSED),
    LoanState.DELINQUENT,            Set.of(LoanState.ACTIVE, LoanState.NPA),
    LoanState.NPA,                   Set.of(LoanState.WRITTEN_OFF)
);
```

### Loan Aggregate

```java
@Entity
public class Loan extends AggregateRoot {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    private LoanState state;

    @Version
    private Long version; // optimistic locking

    private Instant disbursedAt;
    private int daysOverdue;

    public void approve(CreditDecision decision) {
        validateTransition(LoanState.APPROVED);
        this.state = LoanState.APPROVED;
        registerEvent(new LoanApprovedEvent(this.id, decision.getApprovedAmount()));
    }

    public void disburse(DisbursementRequest request) {
        validateTransition(LoanState.DISBURSED);
        this.state = LoanState.DISBURSED;
        this.disbursedAt = Instant.now();
        registerEvent(new LoanDisbursedEvent(this.id, request.getAccountNumber()));
    }

    public void recordPayment(Payment payment) {
        if (this.state == LoanState.DELINQUENT) {
            this.state = LoanState.ACTIVE;
            this.daysOverdue = 0;
            registerEvent(new LoanCuredEvent(this.id));
        }
        registerEvent(new EMIReceivedEvent(this.id, payment));
    }

    public void markMissedPayment(int dpd) {
        this.daysOverdue = dpd;
        if (dpd >= 90 && this.state != LoanState.NPA) {
            validateTransition(LoanState.NPA);
            this.state = LoanState.NPA;
            registerEvent(new LoanNPAEvent(this.id, dpd));
        } else if (this.state == LoanState.ACTIVE) {
            validateTransition(LoanState.DELINQUENT);
            this.state = LoanState.DELINQUENT;
            registerEvent(new LoanDelinquentEvent(this.id, dpd));
        }
    }

    public void close() {
        validateTransition(LoanState.CLOSED);
        this.state = LoanState.CLOSED;
        registerEvent(new LoanClosedEvent(this.id));
    }

    private void validateTransition(LoanState target) {
        Set<LoanState> allowed = VALID_TRANSITIONS.getOrDefault(this.state, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidTransitionException(
                "Cannot transition from " + this.state + " to " + target
            );
        }
    }
}
```

### Outbox Publisher

```java
@Component
@Slf4j
public class LoanEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLoanEvent(LoanDomainEvent event) {
        OutboxMessage message = OutboxMessage.builder()
            .aggregateId(event.getLoanId())
            .aggregateType("LOAN")
            .eventType(event.getClass().getSimpleName())
            .payload(objectMapper.writeValueAsString(event))
            .createdAt(Instant.now())
            .build();
        outboxRepository.save(message);
    }
}

// Scheduled poller — publishes outbox messages to Kafka/SQS
@Component
public class OutboxPoller {

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void poll() {
        List<OutboxMessage> pending = outboxRepository.findByPublishedFalseOrderByCreatedAt();
        pending.forEach(msg -> {
            kafkaTemplate.send("loan-events", msg.getAggregateId(), msg.getPayload());
            msg.markPublished();
            outboxRepository.save(msg);
        });
    }
}
```

---

## Audit / State History Table

```sql
CREATE TABLE loan_state_history (
    id            BIGSERIAL PRIMARY KEY,
    loan_id       VARCHAR(36)  NOT NULL,
    from_state    VARCHAR(50),
    to_state      VARCHAR(50)  NOT NULL,
    triggered_by  VARCHAR(50)  NOT NULL,  -- event name
    actor         VARCHAR(100),           -- user or system
    metadata      JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lsh_loan_id ON loan_state_history(loan_id);
```

---

## DPD Scheduler (Delinquency Detection)

```java
@Component
@Slf4j
public class DPDScheduler {

    private final LoanRepository loanRepository;
    private final LoanLifecycleService lifecycleService;

    @Scheduled(cron = "0 0 1 * * *") // runs daily at 1 AM
    @Transactional
    public void computeDPD() {
        LocalDate today = LocalDate.now();
        List<Loan> activeLoans = loanRepository.findByStateIn(
            List.of(LoanState.ACTIVE, LoanState.DELINQUENT)
        );

        activeLoans.forEach(loan -> {
            int dpd = calculateDPD(loan, today);
            if (dpd > 0) {
                lifecycleService.sendEvent(
                    loan.getId(),
                    LoanEvent.PAYMENT_MISSED,
                    Map.of("daysOverdue", dpd)
                );
            }
        });
    }

    private int calculateDPD(Loan loan, LocalDate today) {
        LocalDate dueDate = loan.getNextEMIDueDate();
        return dueDate.isBefore(today) ? (int) ChronoUnit.DAYS.between(dueDate, today) : 0;
    }
}
```

---

## Decision Framework

| Concern                          | Spring State Machine | Temporal.io   | Domain Events |
|----------------------------------|----------------------|---------------|---------------|
| Transition guard rules           | ✅ Excellent         | ⚠️ Manual    | ✅ Manual     |
| Long-running (months/years)      | ⚠️ Needs persistence | ✅ Native     | ✅ Scheduler  |
| Auto-retry on failure            | ❌                   | ✅ Built-in   | ⚠️ Outbox     |
| Human-in-the-loop signals        | ⚠️ Workaround        | ✅ Signal API | ⚠️ Inbox      |
| Audit trail                      | ✅ With listener     | ✅ Built-in   | ✅ Event store |
| Ops complexity                   | Low                  | Medium        | Low           |
| Multi-step workflow (collections)| ❌                   | ✅ Best       | ❌            |

---

## Recommended Architecture (Production LOS)

```
Spring State Machine        →  Enforces valid transitions + emits domain events
     +
Domain Events + Outbox      →  Durable event delivery to Kafka/SQS
     +
Temporal (optional)         →  Collections workflow, restructuring, human approvals
     +
DPD Scheduler               →  Daily delinquency detection
     +
loan_state_history table    →  Full audit trail
```

This gives you:
- **Invalid transition prevention** at the domain layer
- **Full audit history** in the DB
- **Event-driven side effects** (GL entries, notifications, reporting)
- **Durable long-running workflows** for complex collection/restructuring cases
