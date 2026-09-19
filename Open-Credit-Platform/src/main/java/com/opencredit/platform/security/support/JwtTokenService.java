package com.opencredit.platform.security.support;

import com.opencredit.platform.authority.model.ApprovalLevel;
import com.opencredit.platform.security.model.AppRole;
import com.opencredit.platform.security.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and parses a self-signed HS256 JWT. There is no external IdP to federate against — this
 * platform both issues and validates its own tokens — so a self-signed token is the simplest thing
 * that demonstrates the concept, not a placeholder for something more elaborate. {@code role} and
 * {@code approvalLevel} are embedded as claims so {@link JwtAuthenticationFilter} never needs to
 * hit {@code AppUserRepository} to authenticate a request.
 */
@Component
public class JwtTokenService {

    private static final String ROLE_CLAIM = "role";
    private static final String APPROVAL_LEVEL_CLAIM = "approvalLevel";

    private final SecretKey signingKey;
    private final long expiryMinutes;

    public JwtTokenService(@Value("${security.jwt.secret}") String secret,
                            @Value("${security.jwt.expiry-minutes}") long expiryMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(user.getUsername())
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryMinutes, ChronoUnit.MINUTES)));
        if (user.getApprovalLevel() != null) {
            builder.claim(APPROVAL_LEVEL_CLAIM, user.getApprovalLevel().name());
        }
        return builder.signWith(signingKey).compact();
    }

    public Instant expiryOf(String token) {
        return parse(token).getExpiration().toInstant();
    }

    /** Empty when the token is missing, malformed, expired, or signed with a different key. */
    public Optional<AuthenticatedUser> parseAndValidate(String token) {
        try {
            Claims claims = parse(token);
            AppRole role = AppRole.valueOf(claims.get(ROLE_CLAIM, String.class));
            String approvalLevelClaim = claims.get(APPROVAL_LEVEL_CLAIM, String.class);
            ApprovalLevel approvalLevel = approvalLevelClaim != null ? ApprovalLevel.valueOf(approvalLevelClaim) : null;
            return Optional.of(new AuthenticatedUser(claims.getSubject(), role, approvalLevel));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
