package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.mail.EmailDeliveryException;
import com.prashant.KhetWallah.mail.EmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PasswordRecoveryService {
    private final PasswordRecoveryRepository recoveries;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final EmailSender sender;
    private final Clock clock;
    private final SecureRandom random;

    @Autowired
    public PasswordRecoveryService(PasswordRecoveryRepository recoveries, AppUserRepository users,
            PasswordEncoder encoder, EmailSender sender) {
        this(recoveries, users, encoder, sender, Clock.systemUTC(), new SecureRandom());
    }

    PasswordRecoveryService(PasswordRecoveryRepository recoveries, AppUserRepository users,
            PasswordEncoder encoder, EmailSender sender, Clock clock, SecureRandom random) {
        this.recoveries = recoveries;
        this.users = users;
        this.encoder = encoder;
        this.sender = sender;
        this.clock = clock;
        this.random = random;
    }

    /** Responses deliberately do not reveal account presence, suspension, limits, or mail failures. */
    @Transactional
    public void start(String email) {
        String normalized = normalize(email);
        Instant now = clock.instant();
        recoveries.ensureExists(normalized, now);
        PasswordRecovery recovery = recoveries.findByEmailForUpdate(normalized).orElseThrow();
        recovery.refreshWindow(now);
        if (recovery.getSendCount() >= 5 || recovery.getFailedAttempts() >= 5
                || now.isBefore(recovery.getResendAt())) {
            return;
        }

        // The same hash and persisted budgets are used for known and unknown addresses.
        // Unknown/suspended/admin accounts never receive a code. Admin passwords are
        // managed by private startup configuration, which would overwrite a public reset.
        AppUser eligible = users.findByEmail(normalized)
                .filter(user -> !user.isSuspended() && user.getRole() != UserRole.ADMIN).orElse(null);
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        recovery.issue(encoder.encode(code), now, eligible);
        recoveries.saveAndFlush(recovery);
        if (eligible == null || !sender.isConfigured()) return;
        try {
            sender.send("password-recovery:" + UUID.randomUUID(), normalized,
                    "Reset your KhetWallah password",
                    "Your KhetWallah password recovery code is: " + code
                    + "\n\nIt expires in 10 minutes and can be used once."
                    + "\nDo not share this code with anyone."
                    + "\nIf you did not request a password reset, ignore this email. Your password has not changed.");
        } catch (EmailDeliveryException ignored) {
            // No recipient, code, provider response, or failure cause is logged or surfaced.
            // A provider failure must not distinguish an existing account from an unknown one.
        }
    }

    @Transactional(noRollbackFor = InvalidRecoveryCodeException.class)
    public void finish(String email, String code, String newPassword) {
        if (newPassword == null || newPassword.isBlank()
                || newPassword.length() < 15 || newPassword.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must contain 15 to 128 characters");
        }
        String normalized = normalize(email);
        PasswordRecovery recovery = recoveries.findByEmailForUpdate(normalized)
                .orElseThrow(InvalidRecoveryCodeException::new);
        Instant now = clock.instant();
        recovery.refreshWindow(now);
        if (recovery.getConsumedAt() != null || !now.isBefore(recovery.getExpiresAt())
                || recovery.getFailedAttempts() >= 5) {
            throw new InvalidRecoveryCodeException();
        }
        if (code == null || !code.matches("[0-9]{6}")
                || !encoder.matches(code, recovery.getCodeHash())) {
            recovery.fail();
            recoveries.saveAndFlush(recovery);
            throw new InvalidRecoveryCodeException();
        }
        AppUser user = recovery.getBoundUserId() == null ? null
                : users.findByIdForUpdate(recovery.getBoundUserId()).orElse(null);
        if (user == null || user.isSuspended() || user.getRole() == UserRole.ADMIN
                || !normalized.equals(user.getEmail())
                || recovery.getIssuedAuthVersion() == null
                || user.getAuthVersion() != recovery.getIssuedAuthVersion()) {
            throw new InvalidRecoveryCodeException();
        }
        // Both writes commit together under the recovery + account locks. A later
        // save failure rolls back the password update as well as code consumption.
        user.changePasswordHash(encoder.encode(newPassword));
        users.saveAndFlush(user);
        recovery.consume(now);
        recoveries.saveAndFlush(recovery);
    }

    private String normalize(String email) {
        if (email == null) throw new InvalidRecoveryCodeException();
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 254) throw new InvalidRecoveryCodeException();
        return normalized;
    }
}
