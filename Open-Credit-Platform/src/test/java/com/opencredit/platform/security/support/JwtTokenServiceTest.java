package com.opencredit.platform.security.support;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.security.model.AppRole;
import com.opencredit.platform.security.model.AppUser;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenServiceTest {

    private static final String SECRET = "test-only-secret-key-at-least-32-bytes-long!!";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, 60);

    private static AppUser user(AppRole role, ApprovalLevel approvalLevel) {
        return AppUser.builder()
                .id(UUID.randomUUID())
                .username("bob.checker")
                .passwordHash("irrelevant")
                .role(role)
                .approvalLevel(approvalLevel)
                .active(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void issuedTokenParsesBackToTheSameUsernameRoleAndApprovalLevel() {
        String token = jwtTokenService.issue(user(AppRole.CHECKER, ApprovalLevel.CREDIT_OFFICER));

        Optional<AuthenticatedUser> parsed = jwtTokenService.parseAndValidate(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().username()).isEqualTo("bob.checker");
        assertThat(parsed.get().role()).isEqualTo(AppRole.CHECKER);
        assertThat(parsed.get().approvalLevel()).isEqualTo(ApprovalLevel.CREDIT_OFFICER);
    }

    @Test
    void aMakerTokenCarriesNoApprovalLevel() {
        String token = jwtTokenService.issue(user(AppRole.MAKER, null));

        Optional<AuthenticatedUser> parsed = jwtTokenService.parseAndValidate(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().approvalLevel()).isNull();
    }

    @Test
    void aTamperedTokenFailsValidation() {
        String token = jwtTokenService.issue(user(AppRole.ADMIN, ApprovalLevel.SENIOR_CREDIT_MANAGER));
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtTokenService.parseAndValidate(tampered)).isEmpty();
    }

    @Test
    void aTokenSignedWithADifferentKeyFailsValidation() {
        JwtTokenService otherIssuer = new JwtTokenService("a-completely-different-secret-key-of-32-bytes!", 60);
        String token = otherIssuer.issue(user(AppRole.ADMIN, ApprovalLevel.SENIOR_CREDIT_MANAGER));

        assertThat(jwtTokenService.parseAndValidate(token)).isEmpty();
    }

    @Test
    void garbageInputFailsValidationRatherThanThrowing() {
        assertThat(jwtTokenService.parseAndValidate("not-a-jwt")).isEmpty();
    }

    @Test
    void expiryOfReflectsTheConfiguredExpiryMinutes() {
        Instant before = Instant.now();
        String token = jwtTokenService.issue(user(AppRole.ADMIN, ApprovalLevel.SENIOR_CREDIT_MANAGER));

        Instant expiry = jwtTokenService.expiryOf(token);

        assertThat(expiry).isAfter(before.plusSeconds(59 * 60));
        assertThat(expiry).isBefore(before.plusSeconds(61 * 60));
    }
}
