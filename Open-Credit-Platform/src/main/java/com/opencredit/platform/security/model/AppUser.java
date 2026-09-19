package com.opencredit.platform.security.model;

import com.opencredit.platform.authority.model.ApprovalLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A demo login (Week 10 seeds a handful via {@code 036-seed-demo-users.sql}; there is no
 * self-registration flow). {@code approvalLevel} is only meaningful for a {@link AppRole#CHECKER}
 * — it is what {@link com.opencredit.platform.authority.ApprovalService} compares against an
 * {@link com.opencredit.platform.authority.model.ApprovalCase}'s {@code requiredLevel} in place of
 * the client-asserted value {@code ApprovalActionRequest} used to carry before this table existed.
 */
@Entity
@Table(name = "app_user")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    private UUID id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private AppRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_level")
    private ApprovalLevel approvalLevel;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
