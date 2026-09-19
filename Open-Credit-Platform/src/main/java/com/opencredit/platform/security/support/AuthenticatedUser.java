package com.opencredit.platform.security.support;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.security.model.AppRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * The {@code Authentication} principal set by {@link JwtAuthenticationFilter}: everything a
 * request handler needs about "who is calling," read directly from the token's claims rather than
 * re-queried from {@code AppUserRepository} on every request. That is a deliberate stateless-JWT
 * tradeoff — a role/level change on {@code AppUser} only takes effect the next time that user logs
 * in, not on their next request with an already-issued token.
 */
public record AuthenticatedUser(String username, AppRole role, ApprovalLevel approvalLevel) {

    /**
     * The single place {@code SecurityContextHolder} is read to recover the principal — used by
     * both {@code AuditService} (falls back to {@code "system"}) and {@code ApprovalService} (treats
     * absence as a hard failure), so the extraction itself isn't duplicated between them.
     */
    public static Optional<AuthenticatedUser> current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}
