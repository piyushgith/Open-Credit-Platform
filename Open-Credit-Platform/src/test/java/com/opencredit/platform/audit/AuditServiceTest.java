package com.opencredit.platform.audit;

import com.opencredit.platform.audit.model.AuditBusinessEvent;
import com.opencredit.platform.audit.model.AuditDataChange;
import com.opencredit.platform.audit.model.AuditEventType;
import com.opencredit.platform.audit.repository.AuditBusinessEventRepository;
import com.opencredit.platform.audit.repository.AuditDataChangeRepository;
import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.loan.model.ApplicationStatus;
import com.opencredit.platform.security.model.AppRole;
import com.opencredit.platform.security.support.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditBusinessEventRepository businessEventRepository;

    @Mock
    private AuditDataChangeRepository dataChangeRepository;

    // Constructed per-call (not as a field initializer): field initializers run before
    // MockitoExtension injects the @Mock fields, so an eagerly-built AuditService here would
    // capture null repositories.
    private AuditService auditService() {
        return new AuditService(businessEventRepository, dataChangeRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordEventCapturesTheAuthenticatedPrincipalsUsername() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("alice.maker", AppRole.MAKER, null), null, List.of()));
        UUID entityId = UUID.randomUUID();

        auditService().recordEvent(AuditEventType.APPLICATION_SUBMITTED, "LoanApplication", entityId, "submitted");

        ArgumentCaptor<AuditBusinessEvent> captor = ArgumentCaptor.forClass(AuditBusinessEvent.class);
        verify(businessEventRepository).save(captor.capture());
        AuditBusinessEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo(AuditEventType.APPLICATION_SUBMITTED);
        assertThat(saved.getEntityType()).isEqualTo("LoanApplication");
        assertThat(saved.getEntityId()).isEqualTo(entityId);
        assertThat(saved.getActorUsername()).isEqualTo("alice.maker");
        assertThat(saved.getDetails()).isEqualTo("submitted");
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void recordEventFallsBackToSystemWhenNoAuthenticationIsPresent() {
        UUID entityId = UUID.randomUUID();

        auditService().recordEvent(AuditEventType.UNDERWRITING_STARTED, "UnderwritingAttempt", entityId, null);

        ArgumentCaptor<AuditBusinessEvent> captor = ArgumentCaptor.forClass(AuditBusinessEvent.class);
        verify(businessEventRepository).save(captor.capture());
        assertThat(captor.getValue().getActorUsername()).isEqualTo("system");
    }

    @Test
    void recordDataChangeStringifiesOldAndNewValues() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser("bob.checker", AppRole.CHECKER, ApprovalLevel.CREDIT_OFFICER), null, List.of()));
        UUID entityId = UUID.randomUUID();

        auditService().recordDataChange("LoanApplication", entityId, "status",
                ApplicationStatus.DRAFT, ApplicationStatus.SUBMITTED);

        ArgumentCaptor<AuditDataChange> captor = ArgumentCaptor.forClass(AuditDataChange.class);
        verify(dataChangeRepository).save(captor.capture());
        AuditDataChange saved = captor.getValue();
        assertThat(saved.getField()).isEqualTo("status");
        assertThat(saved.getOldValue()).isEqualTo("DRAFT");
        assertThat(saved.getNewValue()).isEqualTo("SUBMITTED");
        assertThat(saved.getChangedBy()).isEqualTo("bob.checker");
    }

    @Test
    void recordDataChangeToleratesANullOldValue() {
        auditService().recordDataChange("Disbursement", UUID.randomUUID(), "disbursedTotal", null, "50000.00");

        ArgumentCaptor<AuditDataChange> captor = ArgumentCaptor.forClass(AuditDataChange.class);
        verify(dataChangeRepository).save(captor.capture());
        assertThat(captor.getValue().getOldValue()).isNull();
        assertThat(captor.getValue().getNewValue()).isEqualTo("50000.00");
    }
}
