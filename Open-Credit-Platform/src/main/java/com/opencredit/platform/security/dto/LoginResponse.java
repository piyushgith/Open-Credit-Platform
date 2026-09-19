package com.opencredit.platform.security.dto;

import com.opencredit.platform.security.model.AppRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String username;
    private AppRole role;
    private Instant expiresAt;
}
