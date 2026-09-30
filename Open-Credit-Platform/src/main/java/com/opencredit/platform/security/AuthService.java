package com.opencredit.platform.security;

import com.opencredit.platform.security.dto.LoginRequest;
import com.opencredit.platform.security.dto.LoginResponse;
import com.opencredit.platform.security.exception.InvalidCredentialsException;
import com.opencredit.platform.security.model.AppUser;
import com.opencredit.platform.security.repository.AppUserRepository;
import com.opencredit.platform.security.support.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final String unknownUserHash;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                        JwtTokenService jwtTokenService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.unknownUserHash = passwordEncoder.encode("unknown-user");
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByUsernameIgnoreCase(request.getUsername())
                .filter(AppUser::isActive)
                .orElse(null);

        // Unknown/inactive users still pay for a BCrypt check so response time does not reveal them.
        String passwordHash = user != null ? user.getPasswordHash() : unknownUserHash;
        if (!passwordEncoder.matches(request.getPassword(), passwordHash) || user == null) {
            throw new InvalidCredentialsException();
        }

        String token = jwtTokenService.issue(user);
        return LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .role(user.getRole())
                .expiresAt(jwtTokenService.expiryOf(token))
                .build();
    }
}
