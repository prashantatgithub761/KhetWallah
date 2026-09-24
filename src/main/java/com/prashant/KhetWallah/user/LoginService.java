package com.prashant.KhetWallah.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class LoginService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public LoginService(
            AppUserRepository repository,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse checkCredentials(String email, String password) {
        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {
            throw invalidCredentials();
        }

        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);

        AppUser user = repository.findByEmail(normalizedEmail)
                .orElseThrow(this::invalidCredentials);

        boolean passwordMatches = passwordEncoder.matches(
                password,
                user.getPasswordHash()
        );

        if (!passwordMatches) {
            throw invalidCredentials();
        }

        return new UserResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail()
        );
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password"
        );
    }
}