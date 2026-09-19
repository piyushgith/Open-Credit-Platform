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

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                        JwtTokenService jwtTokenService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByUsernameIgnoreCase(request.getUsername())
                .filter(AppUser::isActive)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
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
