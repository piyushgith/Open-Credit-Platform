# Bank Loan Workflow — BPMN with Flowable / Camunda

## Why BPMN, Not State Machine

Banks think in **swimlanes, human tasks, SLA timers, and approval queues** — not state transition tables. BPMN 2.0 engines (Flowable / Camunda) execute the exact diagram your business analyst draws.

| Capability | Spring State Machine | Camunda / Flowable |
|---|---|---|
| Visual diagram (BA readable) | ❌ Code only | ✅ Draw in modeler, deploy |
| Human task queue | ❌ Build yourself | ✅ Built-in task inbox |
| SLA timers / escalation | ❌ Build yourself | ✅ Boundary timer events |
| Parallel approvals | ❌ Complex | ✅ Parallel gateway |
| Audit / history | ❌ Build yourself | ✅ Full history DB |
| Forms on tasks | ❌ | ✅ Embedded forms |
| REST API out of box | ❌ | ✅ Full REST API |

---

## Dependency

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-spring-boot-starter</artifactId>
    <version>7.0.1</version>
</dependency>
```

---

## BPMN Process Definition

Place this file at `src/main/resources/processes/loan-origination.bpmn20.xml`.
Flowable auto-deploys it on startup.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="com.bank.loan">

  <process id="loanOriginationProcess" name="Loan Origination" isExecutable="true">

    <!-- ── Start ── -->
    <startEvent id="start" name="Application received"/>

    <sequenceFlow sourceRef="start" targetRef="reviewApplication"/>

    <!-- ── Lane: Branch Officer ── -->
    <userTask id="reviewApplication" name="Review Application"
              flowable:candidateGroups="branch-officer">
      <documentation>Verify applicant details and check document completeness</documentation>
    </userTask>

    <sequenceFlow sourceRef="reviewApplication" targetRef="docsComplete"/>

    <exclusiveGateway id="docsComplete" name="Docs complete?"/>

    <sequenceFlow sourceRef="docsComplete" targetRef="requestDocs">
      <conditionExpression>${docsComplete == false}</conditionExpression>
    </sequenceFlow>

    <sequenceFlow sourceRef="docsComplete" targetRef="creditCheck">
      <conditionExpression>${docsComplete == true}</conditionExpression>
    </sequenceFlow>

    <!-- Request more docs — loops back to applicant -->
    <userTask id="requestDocs" name="Request Additional Documents"
              flowable:candidateGroups="branch-officer"/>
    <sequenceFlow sourceRef="requestDocs" targetRef="reviewApplication"/>

    <!-- ── Lane: Credit Team ── -->
    <userTask id="creditCheck" name="Credit Bureau Check"
              flowable:candidateGroups="credit-team">
      <extensionElements>
        <flowable:taskListener event="create"
          class="com.bank.loan.listeners.SLATaskListener"/>
      </extensionElements>
    </userTask>

    <!-- SLA: escalate to senior credit officer if no action in 48 hours -->
    <boundaryEvent id="creditCheckTimer" attachedToRef="creditCheck" cancelActivity="false">
      <timerEventDefinition>
        <timeDuration>PT48H</timeDuration>
      </timerEventDefinition>
    </boundaryEvent>
    <sequenceFlow sourceRef="creditCheckTimer" targetRef="escalateToSenior"/>

    <userTask id="escalateToSenior" name="Escalated Credit Review"
              flowable:candidateGroups="senior-credit-officer"/>
    <sequenceFlow sourceRef="escalateToSenior" targetRef="riskScoring"/>

    <sequenceFlow sourceRef="creditCheck" targetRef="riskScoring"/>

    <!-- Risk scoring runs in parallel with legal check -->
    <parallelGateway id="parallelStart" name="Start parallel checks"/>
    <sequenceFlow sourceRef="riskScoring" targetRef="parallelStart"/>

    <serviceTask id="riskScoring" name="Risk Scoring"
                 flowable:class="com.bank.loan.delegates.RiskScoringDelegate"/>

    <userTask id="legalCheck" name="Legal / Title Verification"
              flowable:candidateGroups="legal-team"/>

    <serviceTask id="bureauCheck" name="CIBIL / Bureau Pull"
                 flowable:class="com.bank.loan.delegates.CreditBureauDelegate"/>

    <sequenceFlow sourceRef="parallelStart" targetRef="legalCheck"/>
    <sequenceFlow sourceRef="parallelStart" targetRef="bureauCheck"/>

    <!-- Wait for both to complete -->
    <parallelGateway id="parallelJoin" name="All checks done"/>
    <sequenceFlow sourceRef="legalCheck"   targetRef="parallelJoin"/>
    <sequenceFlow sourceRef="bureauCheck"  targetRef="parallelJoin"/>

    <sequenceFlow sourceRef="parallelJoin" targetRef="creditDecision"/>

    <!-- Credit committee decision -->
    <userTask id="creditDecision" name="Credit Committee Decision"
              flowable:candidateGroups="credit-committee">
      <extensionElements>
        <!-- Auto-reject if not actioned in 7 days -->
        <flowable:taskListener event="create"
          class="com.bank.loan.listeners.AutoRejectListener"/>
      </extensionElements>
    </userTask>

    <boundaryEvent id="autoRejectTimer" attachedToRef="creditDecision" cancelActivity="true">
      <timerEventDefinition>
        <timeDuration>P7D</timeDuration>
      </timerEventDefinition>
    </boundaryEvent>
    <sequenceFlow sourceRef="autoRejectTimer" targetRef="rejectEnd"/>

    <sequenceFlow sourceRef="creditDecision" targetRef="approvalGateway"/>

    <exclusiveGateway id="approvalGateway" name="Approved?"/>

    <sequenceFlow sourceRef="approvalGateway" targetRef="generateSanction">
      <conditionExpression>${creditApproved == true}</conditionExpression>
    </sequenceFlow>

    <sequenceFlow sourceRef="approvalGateway" targetRef="rejectEnd">
      <conditionExpression>${creditApproved == false}</conditionExpression>
    </sequenceFlow>

    <!-- ── Lane: Disbursement ── -->
    <userTask id="generateSanction" name="Generate Sanction Letter"
              flowable:candidateGroups="disbursement-team"/>

    <!-- SLA: sanction letter must be issued within 2 business days -->
    <boundaryEvent id="sanctionSLA" attachedToRef="generateSanction" cancelActivity="false">
      <timerEventDefinition>
        <timeDuration>P2D</timeDuration>
      </timerEventDefinition>
    </boundaryEvent>
    <sequenceFlow sourceRef="sanctionSLA" targetRef="escalateSanction"/>

    <userTask id="escalateSanction" name="Escalate Sanction Delay"
              flowable:candidateGroups="branch-manager"/>
    <sequenceFlow sourceRef="escalateSanction" targetRef="generateSanction"/>

    <sequenceFlow sourceRef="generateSanction" targetRef="signAgreement"/>

    <userTask id="signAgreement" name="Sign Loan Agreement"
              flowable:assignee="${applicantId}">
      <documentation>Applicant signs the agreement — e-sign or physical</documentation>
    </userTask>

    <sequenceFlow sourceRef="signAgreement" targetRef="disburse"/>

    <serviceTask id="disburse" name="Disburse Funds (NEFT/RTGS)"
                 flowable:class="com.bank.loan.delegates.DisbursementDelegate"/>

    <sequenceFlow sourceRef="disburse" targetRef="disbursedEnd"/>

    <!-- ── End events ── -->
    <endEvent id="disbursedEnd" name="Loan Disbursed"/>
    <endEvent id="rejectEnd"    name="Application Rejected"/>

  </process>
</definitions>
```

---

## Service Delegates

### Risk Scoring Delegate

```java
@Component
public class RiskScoringDelegate implements JavaDelegate {

    private final RiskEngine riskEngine;

    @Override
    public void execute(DelegateExecution execution) {
        String applicantId = (String) execution.getVariable("applicantId");
        BigDecimal amount  = (BigDecimal) execution.getVariable("loanAmount");

        RiskScore score = riskEngine.score(applicantId, amount);

        execution.setVariable("riskScore",    score.getValue());
        execution.setVariable("riskCategory", score.getCategory().name()); // LOW/MEDIUM/HIGH
        execution.setVariable("dtiRatio",     score.getDtiRatio());
        execution.setVariable("ltvRatio",     score.getLtvRatio());
    }
}
```

### Disbursement Delegate

```java
@Component
public class DisbursementDelegate implements JavaDelegate {

    private final CoreBankingService cbs;
    private final LoanRepository loanRepository;

    @Override
    public void execute(DelegateExecution execution) {
        String loanId     = (String) execution.getVariable("applicationId");
        String accountNo  = (String) execution.getVariable("disbursementAccount");
        BigDecimal amount = (BigDecimal) execution.getVariable("sanctionedAmount");

        DisbursementResult result = cbs.disburse(
            DisbursementRequest.builder()
                .loanId(loanId)
                .accountNumber(accountNo)
                .amount(amount)
                .mode(PaymentMode.NEFT)
                .build()
        );

        execution.setVariable("disbursementRef", result.getTransactionRef());
        execution.setVariable("disbursedAt",     result.getTimestamp().toString());

        loanRepository.updateState(loanId, LoanState.DISBURSED);
    }
}
```

---

## Task Listeners

### SLA Task Listener

```java
@Component
public class SLATaskListener implements TaskListener {

    private final NotificationService notificationService;

    @Override
    public void notify(DelegateTask task) {
        // Fires on task CREATE — record SLA start time
        task.setVariableLocal("slaStartedAt", Instant.now().toString());

        notificationService.notifyGroup(
            task.getTaskDefinitionKey(),
            task.getId(),
            "New task assigned with SLA: " + task.getName()
        );
    }
}
```

### Auto-Reject Listener (timer boundary)

```java
@Component
public class AutoRejectListener implements JavaDelegate {

    private final LoanRepository loanRepository;
    private final NotificationService notificationService;

    @Override
    public void execute(DelegateExecution execution) {
        String applicationId = (String) execution.getVariable("applicationId");

        loanRepository.updateState(applicationId, LoanState.REJECTED);

        notificationService.notifyApplicant(
            applicationId,
            "Your loan application was auto-rejected due to no decision within 7 days."
        );
    }
}
```

---

## Application Service

```java
@Service
@Slf4j
public class LoanProcessService {

    private final RuntimeService  runtimeService;
    private final TaskService     taskService;
    private final HistoryService  historyService;
    private final ManagementService managementService;

    // ── Start a new loan workflow instance ──
    public String startLoanProcess(LoanApplication application) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("applicantId",         application.getApplicantId());
        variables.put("loanAmount",          application.getAmount());
        variables.put("loanProduct",         application.getProductType());
        variables.put("applicationId",       application.getId());
        variables.put("disbursementAccount", application.getAccountNumber());
        variables.put("docsComplete",        false);

        ProcessInstance instance = runtimeService.startProcessInstanceByKey(
            "loanOriginationProcess",
            application.getId(), // business key — use loanId for lookups
            variables
        );

        log.info("Started loan process {} for application {}",
            instance.getId(), application.getId());

        return instance.getId();
    }

    // ── Branch officer completes document review ──
    public void completeDocumentReview(String taskId, boolean docsComplete) {
        taskService.complete(taskId, Map.of("docsComplete", docsComplete));
    }

    // ── Credit officer completes the decision ──
    public void completeCreditDecision(String taskId,
                                       boolean approved,
                                       BigDecimal sanctionedAmount,
                                       String remarks) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("creditApproved",   approved);
        vars.put("sanctionedAmount", sanctionedAmount);
        vars.put("creditRemarks",    remarks);

        taskService.complete(taskId, vars);
    }

    // ── Query pending tasks by role ──
    public List<Task> getPendingTasksForGroup(String group) {
        return taskService.createTaskQuery()
            .taskCandidateGroup(group)
            .orderByTaskCreateTime().asc()
            .list();
    }

    // ── Query tasks for a specific loan ──
    public List<Task> getActiveTasksForLoan(String applicationId) {
        return taskService.createTaskQuery()
            .processInstanceBusinessKey(applicationId)
            .orderByTaskCreateTime().asc()
            .list();
    }

    // ── Full audit trail ──
    public List<HistoricActivityInstance> getLoanHistory(String applicationId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(applicationId)
            .singleResult();

        return historyService.createHistoricActivityInstanceQuery()
            .processInstanceId(pi.getId())
            .finished()
            .orderByHistoricActivityInstanceStartTime().asc()
            .list();
    }

    // ── Current state of a loan process ──
    public String getCurrentActivity(String applicationId) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
            .processInstanceBusinessKey(applicationId)
            .singleResult();

        if (pi == null) return "COMPLETED";

        List<Execution> executions = runtimeService.createExecutionQuery()
            .processInstanceId(pi.getId())
            .onlyChildExecutions()
            .list();

        return executions.stream()
            .map(e -> runtimeService.getActiveActivityIds(e.getId()))
            .flatMap(List::stream)
            .collect(Collectors.joining(", "));
    }
}
```

---

## REST Controller

```java
@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanProcessController {

    private final LoanProcessService processService;

    @PostMapping
    public ResponseEntity<Map<String, String>> startLoan(
            @RequestBody @Valid LoanApplicationRequest request) {
        String processId = processService.startLoanProcess(request.toApplication());
        return ResponseEntity.ok(Map.of("processInstanceId", processId));
    }

    @GetMapping("/{applicationId}/tasks")
    public ResponseEntity<List<TaskDto>> getActiveTasks(
            @PathVariable String applicationId) {
        List<Task> tasks = processService.getActiveTasksForLoan(applicationId);
        return ResponseEntity.ok(tasks.stream().map(TaskDto::from).toList());
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> completeTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> variables) {
        processService.completeTask(taskId, variables);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{applicationId}/history")
    public ResponseEntity<List<HistoryDto>> getLoanHistory(
            @PathVariable String applicationId) {
        List<HistoricActivityInstance> history =
            processService.getLoanHistory(applicationId);
        return ResponseEntity.ok(history.stream().map(HistoryDto::from).toList());
    }

    @GetMapping("/{applicationId}/status")
    public ResponseEntity<Map<String, String>> getCurrentStatus(
            @PathVariable String applicationId) {
        String activity = processService.getCurrentActivity(applicationId);
        return ResponseEntity.ok(Map.of("currentActivity", activity));
    }
}
```

---

## Architecture Layers

```
┌─────────────────────────────────────────────────────┐
│  BPMN Engine (Flowable)                             │
│  • Executes loan-origination.bpmn20.xml             │
│  • Manages human task queues per role               │
│  • Fires SLA timers and escalations                 │
│  • Stores full audit history in ACT_HI_* tables     │
└──────────────┬──────────────────────────────────────┘
               │ Service Tasks call
┌──────────────▼──────────────────────────────────────┐
│  Spring Services / Delegates                        │
│  • RiskScoringDelegate                              │
│  • CreditBureauDelegate                             │
│  • DisbursementDelegate                             │
└──────────────┬──────────────────────────────────────┘
               │ Domain rules enforced by
┌──────────────▼──────────────────────────────────────┐
│  Loan Aggregate (optional Spring State Machine)     │
│  • Prevents invalid state jumps in loan entity      │
│  • Emits domain events on state change              │
└──────────────┬──────────────────────────────────────┘
               │ Domain events published via
┌──────────────▼──────────────────────────────────────┐
│  Outbox → Kafka                                     │
│  • GL / CBS notifications                           │
│  • Reporting pipeline                               │
│  • Applicant notifications                          │
└─────────────────────────────────────────────────────┘
```

---

## Flowable History Tables (Auto-managed)

| Table | Contains |
|---|---|
| `ACT_HI_PROCINST` | All process instances — start, end, duration |
| `ACT_HI_TASKINST` | All human tasks — assignee, completion time |
| `ACT_HI_ACTINST` | Every activity executed, with timestamps |
| `ACT_HI_VARINST` | All process variable history |
| `ACT_HI_COMMENT`  | Notes added by officers during review |

No custom audit code needed — Flowable writes all of this automatically.

---

## application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/loandb
    username: ${DB_USER}
    password: ${DB_PASS}

flowable:
  async-executor-activate: true
  history-level: full           # audit everything
  database-schema-update: true  # auto-create ACT_* tables
  process:
    definition-location-prefix: classpath:/processes/
```

---

## Summary

- **Flowable / Camunda** executes the BPMN diagram your BA drew — swimlanes, human tasks, SLA timers, escalations, parallel checks
- **Service delegates** are Spring beans called from BPMN service tasks — risk scoring, bureau pull, disbursement
- **Human tasks** go into role-based queues — credit officers, branch managers, legal team all have their own inbox
- **SSM (optional)** enforces entity-level invariants inside the loan aggregate
- **Outbox → Kafka** delivers domain events to downstream systems
