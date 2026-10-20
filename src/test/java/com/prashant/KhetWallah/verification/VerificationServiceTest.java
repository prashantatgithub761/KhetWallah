package com.prashant.KhetWallah.verification;

import com.prashant.KhetWallah.config.PasswordConfig;
import com.prashant.KhetWallah.mail.EmailDeliveryException;
import com.prashant.KhetWallah.mail.EmailSender;
import com.prashant.KhetWallah.user.AppUser;
import com.prashant.KhetWallah.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VerificationServiceTest {
    private static final String EMAIL = "farmer@example.com";
    private final EmailVerificationRepository repository = mock(EmailVerificationRepository.class);
    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final EmailSender sender = mock(EmailSender.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SecureRandom random = mock(SecureRandom.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-17T10:00:00Z"));
    private EmailVerification stored;
    private VerificationService service;

    @BeforeEach
    void setUp() {
        service = new VerificationService(repository, userRepository, encoder, sender, clock, random);
        when(sender.isConfigured()).thenReturn(true);
        when(random.nextInt(1_000_000)).thenReturn(123456);
        when(encoder.encode(anyString())).thenReturn("opaque-code-hash");
        when(encoder.matches(anyString(), anyString())).thenAnswer(invocation ->
                "123456".equals(invocation.getArgument(0))
                        && "opaque-code-hash".equals(invocation.getArgument(1)));
        when(repository.findByEmailForUpdate(anyString())).thenAnswer(invocation ->
                stored != null && stored.getEmail().equals(invocation.getArgument(0))
                        ? Optional.of(stored) : Optional.empty());
        when(repository.findByChallengeIdForUpdate(anyString())).thenAnswer(invocation ->
                stored != null && stored.getChallengeId().equals(invocation.getArgument(0))
                        ? Optional.of(stored) : Optional.empty());
        when(repository.saveAndFlush(any(EmailVerification.class))).thenAnswer(invocation -> {
            stored = invocation.getArgument(0);
            return stored;
        });
        doAnswer(invocation -> {
            stored = null;
            return null;
        }).when(repository).delete(any(EmailVerification.class));
    }

    @Test
    void startStoresOnlyAHashAndSendsCodeWithoutCreatingAnAccount() {
        PasswordEncoder actualEncoder = new PasswordConfig().passwordEncoder();
        service = new VerificationService(repository, userRepository, actualEncoder, sender, clock, random);

        VerificationChallenge result = service.start("  Farmer@Example.COM  ");

        assertEquals(EMAIL, stored.getEmail());
        assertNotEquals("123456", stored.getCodeHash());
        assertTrue(actualEncoder.matches("123456", stored.getCodeHash()));
        assertEquals(clock.instant().plusSeconds(600), result.expiresAt());
        assertEquals(60, result.resendAfterSeconds());
        assertEquals(36, result.challengeId().length());
        assertEquals(1, stored.getSendCount());
        assertEquals(0, stored.getFailedAttempts());
        assertFalse(result.toString().contains("123456"));
        assertFalse(stored.toString().contains("123456"));
        var inOrder = inOrder(repository, sender);
        inOrder.verify(repository).saveAndFlush(any(EmailVerification.class));
        inOrder.verify(sender).send(startsWith("signup-verification:"), eq(EMAIL),
                eq("Your KhetWallah verification code"), contains("123456"));
        verify(userRepository, never()).saveAndFlush(any(AppUser.class));
        verify(userRepository, never()).save(any(AppUser.class));
    }

    @Test
    void secureRandomCodeIncludesLeadingZeros() {
        when(random.nextInt(1_000_000)).thenReturn(7);

        service.start(EMAIL);

        verify(encoder).encode("000007");
        verify(sender).send(anyString(), eq(EMAIL), anyString(), contains("000007"));
    }

    @Test
    void rejectsExistingAccountsBeforeIssuingCode() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertStatus(HttpStatus.CONFLICT, () -> service.start(EMAIL));

        verifyNoInteractions(repository, encoder);
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsUnconfiguredEmailWithoutSavingOrCreatingAnAccount() {
        when(sender.isConfigured()).thenReturn(false);

        assertStatus(HttpStatus.SERVICE_UNAVAILABLE, () -> service.start(EMAIL));

        verifyNoInteractions(repository, encoder);
        verify(userRepository, never()).saveAndFlush(any(AppUser.class));
    }

    @Test
    void failedEmailDeliveryReturnsOnlySanitizedServiceUnavailable() {
        doThrow(new EmailDeliveryException()).when(sender)
                .send(anyString(), anyString(), anyString(), anyString());

        ResponseStatusException exception = assertStatus(HttpStatus.SERVICE_UNAVAILABLE,
                () -> service.start(EMAIL));

        assertNull(exception.getCause());
        assertFalse(exception.getReason().contains(EMAIL));
        assertFalse(exception.getReason().contains("123456"));
        assertTrue(transactionFor("start", String.class).rollbackOn(exception));
        verify(userRepository, never()).saveAndFlush(any(AppUser.class));
    }

    @Test
    void concurrentFirstInsertAndGapLockRacesReturnConflict() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("constraint"));
        assertStatus(HttpStatus.CONFLICT, () -> service.start(EMAIL));
        doThrow(new PessimisticLockingFailureException("deadlock"))
                .when(repository).saveAndFlush(any());
        assertStatus(HttpStatus.CONFLICT, () -> service.start(EMAIL));
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void correctProofIsConsumedOnceUnderTheEmailLock() {
        VerificationChallenge challenge = service.start(EMAIL);

        service.verifyAndConsume("FARMER@EXAMPLE.COM", challenge.challengeId(), "123456");

        verify(repository, times(2)).findByEmailForUpdate(EMAIL);
        verify(repository).delete(any(EmailVerification.class));
        verify(repository).flush();
        assertNull(stored);
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.verifyAndConsume(EMAIL, challenge.challengeId(), "123456"));
    }

    @Test
    void expiredCodeAndMissingOrMismatchedChallengeHaveTheSameGenericError() {
        VerificationChallenge challenge = service.start(EMAIL);
        ResponseStatusException mismatched = assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.verifyAndConsume(EMAIL, "another-challenge", "123456"));
        ResponseStatusException missing = assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.verifyAndConsume("missing@example.com", challenge.challengeId(), "123456"));
        clock.advance(Duration.ofMinutes(10));
        ResponseStatusException expired = assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.verifyAndConsume(EMAIL, challenge.challengeId(), "123456"));

        assertEquals(mismatched.getReason(), missing.getReason());
        assertEquals(missing.getReason(), expired.getReason());
        assertEquals(0, stored.getFailedAttempts());
        verify(repository, never()).delete(any());
    }

    @Test
    void wrongCodesPersistTheirAttemptAndOnlyThatExceptionAvoidsRollback() throws Exception {
        VerificationChallenge challenge = service.start(EMAIL);

        InvalidCodeException exception = assertThrows(InvalidCodeException.class,
                () -> service.verifyAndConsume(EMAIL, challenge.challengeId(), "654321"));

        assertEquals(1, stored.getFailedAttempts());
        verify(repository, times(2)).saveAndFlush(stored);
        Transactional annotation = VerificationService.class.getMethod("verifyAndConsume",
                String.class, String.class, String.class).getAnnotation(Transactional.class);
        assertEquals(Propagation.MANDATORY, annotation.propagation());
        assertArrayEquals(new Class<?>[] {InvalidCodeException.class}, annotation.noRollbackFor());
        TransactionAttribute transaction = transactionFor("verifyAndConsume",
                String.class, String.class, String.class);
        assertFalse(transaction.rollbackOn(exception));
        assertTrue(transaction.rollbackOn(new ResponseStatusException(HttpStatus.BAD_REQUEST)));
    }

    @Test
    void fiveWrongCodesExhaustTheHourlyBudgetEvenForTheCorrectCode() {
        VerificationChallenge challenge = service.start(EMAIL);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(InvalidCodeException.class,
                    () -> service.verifyAndConsume(EMAIL, challenge.challengeId(), "654321"));
        }

        assertStatus(HttpStatus.TOO_MANY_REQUESTS,
                () -> service.verifyAndConsume(EMAIL, challenge.challengeId(), "123456"));
        clock.advance(Duration.ofMinutes(1));
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> service.resend(challenge.challengeId()));
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> service.start(EMAIL));
        assertEquals(5, stored.getFailedAttempts());
        verify(repository, never()).delete(any());
    }

    @Test
    void resendAndNewStartRespectCooldownAndPreserveBothBudgets() {
        VerificationChallenge initial = service.start(EMAIL);
        assertThrows(InvalidCodeException.class,
                () -> service.verifyAndConsume(EMAIL, initial.challengeId(), "654321"));

        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> service.resend(initial.challengeId()));
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> service.start(EMAIL));
        clock.advance(Duration.ofSeconds(60));
        VerificationChallenge resent = service.resend(initial.challengeId());
        assertEquals(initial.challengeId(), resent.challengeId());
        assertEquals(2, stored.getSendCount());
        assertEquals(1, stored.getFailedAttempts());

        clock.advance(Duration.ofSeconds(60));
        VerificationChallenge restarted = service.start(EMAIL);
        assertNotEquals(initial.challengeId(), restarted.challengeId());
        assertEquals(3, stored.getSendCount());
        assertEquals(1, stored.getFailedAttempts());
        assertStatus(HttpStatus.BAD_REQUEST,
                () -> service.verifyAndConsume(EMAIL, initial.challengeId(), "123456"));
    }

    @Test
    void resendChangesTheCodeAndInvalidatesThePreviousOne() {
        PasswordEncoder actualEncoder = new PasswordConfig().passwordEncoder();
        service = new VerificationService(repository, userRepository, actualEncoder, sender, clock, random);
        when(random.nextInt(1_000_000)).thenReturn(123456, 654321);
        VerificationChallenge challenge = service.start(EMAIL);
        String oldHash = stored.getCodeHash();
        clock.advance(Duration.ofMinutes(1));

        service.resend(challenge.challengeId());

        assertNotEquals(oldHash, stored.getCodeHash());
        assertThrows(InvalidCodeException.class,
                () -> service.verifyAndConsume(EMAIL, challenge.challengeId(), "123456"));
        service.verifyAndConsume(EMAIL, challenge.challengeId(), "654321");
        assertNull(stored);
    }

    @Test
    void fiveSuccessfulSendsAreTheSharedLimitForResendsAndNewStarts() {
        VerificationChallenge challenge = service.start(EMAIL);
        for (int send = 1; send < 5; send++) {
            clock.advance(Duration.ofMinutes(1));
            challenge = send % 2 == 0 ? service.start(EMAIL) : service.resend(challenge.challengeId());
        }
        clock.advance(Duration.ofMinutes(1));
        String lastChallengeId = challenge.challengeId();

        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> service.resend(lastChallengeId));
        assertStatus(HttpStatus.TOO_MANY_REQUESTS, () -> service.start(EMAIL));
        assertEquals(5, stored.getSendCount());
        verify(sender, times(5)).send(anyString(), eq(EMAIL), anyString(), anyString());
    }

    @Test
    void onlyAnExpiredHourResetsBothBudgets() {
        VerificationChallenge challenge = service.start(EMAIL);
        assertThrows(InvalidCodeException.class,
                () -> service.verifyAndConsume(EMAIL, challenge.challengeId(), "654321"));
        Instant firstWindow = stored.getWindowStartedAt();
        clock.advance(Duration.ofMinutes(59));
        service.start(EMAIL);
        assertEquals(firstWindow, stored.getWindowStartedAt());
        assertEquals(2, stored.getSendCount());
        assertEquals(1, stored.getFailedAttempts());

        clock.advance(Duration.ofMinutes(1));
        service.start(EMAIL);

        assertEquals(clock.instant(), stored.getWindowStartedAt());
        assertEquals(1, stored.getSendCount());
        assertEquals(0, stored.getFailedAttempts());
    }

    @Test
    void repositoryLockQueriesUsePessimisticWrite() throws Exception {
        for (String method : new String[] {"findByEmailForUpdate", "findByChallengeIdForUpdate"}) {
            var lock = EmailVerificationRepository.class.getMethod(method, String.class)
                    .getAnnotation(org.springframework.data.jpa.repository.Lock.class);
            assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, lock.value());
        }
    }

    private TransactionAttribute transactionFor(String name, Class<?>... parameters) {
        try {
            return new AnnotationTransactionAttributeSource().getTransactionAttribute(
                    VerificationService.class.getMethod(name, parameters), VerificationService.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private ResponseStatusException assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, exception.getStatusCode());
        return exception;
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) { this.now = now; }
        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
