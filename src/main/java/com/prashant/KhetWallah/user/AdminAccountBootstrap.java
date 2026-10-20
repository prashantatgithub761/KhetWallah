package com.prashant.KhetWallah.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class AdminAccountBootstrap implements ApplicationRunner {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminAccountBootstrap(
            AppUserRepository users,
            PasswordEncoder encoder,
            @Value("${ADMIN_EMAIL:}") String email,
            @Value("${ADMIN_PASSWORD:}") String password
    ) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank() || password.length() < 8) {
            throw new IllegalStateException(
                    "Set both ADMIN_EMAIL and an ADMIN_PASSWORD of at least 8 characters"
            );
        }

        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (!normalized.contains("@")) {
            throw new IllegalStateException("ADMIN_EMAIL must be an email address");
        }

        AppUser account = users.findByEmail(normalized).orElse(null);
        if (account == null) {
            account = new AppUser("KhetWallah Admin", normalized, encoder.encode(password));
            account.makeAdmin();
            users.saveAndFlush(account);
        } else if (account.getRole() != UserRole.ADMIN) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL belongs to a regular account; use another email"
            );
        } else {
            // Updating the private environment variable rotates the admin password.
            if (!encoder.matches(password, account.getPasswordHash())) {
                account.changePasswordHash(encoder.encode(password));
            }
        }
    }
}
