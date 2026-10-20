package com.prashant.KhetWallah.verification;

import com.prashant.KhetWallah.mail.EmailDeliveryException;
import com.prashant.KhetWallah.mail.EmailSender;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class VerificationService {
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration LIMIT_WINDOW = Duration.ofHours(1);
    private static final int MAX_SENDS = 5;
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String INVALID_CODE = "The verification code is invalid or has expired";

    private final EmailVerificationRepository verificationRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final Clock clock;
    private final SecureRandom random;

    @Autowired
    public VerificationService(
            EmailVerificationRepository verificationRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender
    ) {
        this(verificationRepository, userRepository, passwordEncoder, emailSender,
                Clock.systemUTC(), new SecureRandom());
    }

    VerificationService(
            EmailVerificationRepository verificationRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            Clock clock,
            SecureRandom random
    ) {
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.clock = clock;
        this.random = random;
    }

    @Transactional
    public VerificationChallenge start(String email) {
        String normalizedEmail = normalizeEmail(email);
        rejectExistingAccount(normalizedEmail);
        requireEmailSender();

        try {
            Instant now = clock.instant();
            EmailVerification verification = verificationRepository
                    .findByEmailForUpdate(normalizedEmail)
                    .orElseGet(() -> new EmailVerification(normalizedEmail, now));
            prepareToSend(verification, now);
            return sendCode(verification, UUID.randomUUID().toString(), now);
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException exception) {
            // An absent row cannot itself be locked. The primary key is the final
            // guard when two first requests race (including an InnoDB gap-lock deadlock).
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A verification request is already in progress; please try again");
        }
    }

    @Transactional
    public VerificationChallenge resend(String challengeId) {
        requireEmailSender();
        EmailVerification verification = verificationRepository
                .findByChallengeIdForUpdate(challengeId)
                .orElseThrow(this::invalidChallenge);
        rejectExistingAccount(verification.getEmail());
        Instant now = clock.instant();
        prepareToSend(verification, now);
        return sendCode(verification, verification.getChallengeId(), now);
    }

    /** The caller must also preserve InvalidCodeException, and register in this transaction. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = InvalidCodeException.class)
    public void verifyAndConsume(String email, String challengeId, String code) {
        String normalizedEmail = normalizeEmail(email);
        EmailVerification verification = verificationRepository
                .findByEmailForUpdate(normalizedEmail)
                .orElseThrow(this::invalidChallenge);
        Instant now = clock.instant();

        if (!verification.getChallengeId().equals(challengeId)
                || !now.isBefore(verification.getExpiresAt())) {
            throw invalidChallenge();
        }

        verification.refreshWindow(now, LIMIT_WINDOW);
        if (verification.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw tooManyAttempts();
        }

        if (code == null || !code.matches("[0-9]{6}")
                || !passwordEncoder.matches(code, verification.getCodeHash())) {
            verification.recordFailedAttempt();
            verificationRepository.saveAndFlush(verification);
            throw new InvalidCodeException();
        }

        verificationRepository.delete(verification);
        // The lock stays held until VerifiedRegistrationService also creates the account.
        verificationRepository.flush();
    }

    private void prepareToSend(EmailVerification verification, Instant now) {
        verification.refreshWindow(now, LIMIT_WINDOW);
        if (verification.getSendCount() >= MAX_SENDS
                || verification.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw tooManyAttempts();
        }
        if (verification.getResendAt() != null && now.isBefore(verification.getResendAt())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait 60 seconds before requesting another code");
        }
    }

    private VerificationChallenge sendCode(
            EmailVerification verification, String challengeId, Instant now
    ) {
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        verification.issueCode(challengeId, passwordEncoder.encode(code),
                now.plus(CODE_LIFETIME), now.plus(RESEND_COOLDOWN));
        verificationRepository.saveAndFlush(verification);

        try {
            emailSender.send("signup-verification:" + UUID.randomUUID(), verification.getEmail(),
                    "Your KhetWallah verification code",
                    "Your KhetWallah signup verification code is: " + code
                            + "\n\nThis code expires in 10 minutes."
                            + "\nIf you did not request this code, you can ignore this email.");
        } catch (EmailDeliveryException exception) {
            // No cause, recipient, code, or provider response is exposed or logged.
            throw emailUnavailable();
        }

        return new VerificationChallenge(challengeId, verification.getExpiresAt(),
                (int) RESEND_COOLDOWN.toSeconds());
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw invalidChallenge();
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 254) {
            throw invalidChallenge();
        }
        return normalized;
    }

    private void rejectExistingAccount(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An account with this email already exists");
        }
    }

    private void requireEmailSender() {
        if (!emailSender.isConfigured()) {
            throw emailUnavailable();
        }
    }

    private ResponseStatusException invalidChallenge() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CODE);
    }

    private ResponseStatusException tooManyAttempts() {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many verification attempts; please try again after one hour");
    }

    private ResponseStatusException emailUnavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Email verification is temporarily unavailable; please try again later");
    }
}
