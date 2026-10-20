package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.config.PasswordConfig;
import com.prashant.KhetWallah.mail.EmailDeliveryException;
import com.prashant.KhetWallah.mail.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
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

class PasswordRecoveryServiceTest {
    private static final String EMAIL = "farmer@example.com";
    private static final String NEW_PASSWORD = "A fresh unique passphrase 2026!";
    private final PasswordRecoveryRepository recoveries = mock(PasswordRecoveryRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final EmailSender sender = mock(EmailSender.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final SecureRandom random = mock(SecureRandom.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
    private PasswordRecoveryService service;
    private PasswordRecovery stored;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new PasswordRecoveryService(recoveries, users, encoder, sender, clock, random);
        user = new AppUser("Farmer", EMAIL, "old-password-hash");
        ReflectionTestUtils.setField(user, "id", 7L);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
        when(sender.isConfigured()).thenReturn(true);
        when(random.nextInt(1_000_000)).thenReturn(123456);
        when(encoder.encode(anyString())).thenAnswer(invocation ->
                NEW_PASSWORD.equals(invocation.getArgument(0)) ? "new-password-hash" : "opaque-code-hash");
        when(encoder.matches(anyString(), anyString())).thenAnswer(invocation ->
                "123456".equals(invocation.getArgument(0))
                        && "opaque-code-hash".equals(invocation.getArgument(1)));
        doAnswer(invocation -> {
            if (stored == null) stored = new PasswordRecovery(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(recoveries).ensureExists(anyString(), any(Instant.class));
        when(recoveries.findByEmailForUpdate(anyString())).thenAnswer(invocation ->
                stored != null && stored.getEmail().equals(invocation.getArgument(0))
                        ? Optional.of(stored) : Optional.empty());
        when(recoveries.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void normalizedStartStoresOnlyHashAndLeavesAccountPasswordUntouched() {
        PasswordEncoder actual = new PasswordConfig().passwordEncoder();
        service = new PasswordRecoveryService(recoveries, users, actual, sender, clock, random);
        service.start(" FARMER@Example.COM ");
        assertTrue(actual.matches("123456", stored.getCodeHash()));
        assertNotEquals("123456", stored.getCodeHash());
        assertEquals(clock.instant().plusSeconds(600), stored.getExpiresAt());
        assertEquals(7L, stored.getBoundUserId());
        assertEquals(0L, stored.getIssuedAuthVersion());
        assertEquals(1, stored.getSendCount());
        assertEquals("old-password-hash", user.getPasswordHash());
        verify(sender).send(startsWith("password-recovery:"), eq(EMAIL),
                eq("Reset your KhetWallah password"), contains("123456"));
        assertFalse(stored.toString().contains(EMAIL));
        assertFalse(stored.toString().contains("123456"));
    }

    @Test
    void codeMayIncludeLeadingZeros() {
        when(random.nextInt(1_000_000)).thenReturn(7);
        service.start(EMAIL);
        verify(encoder).encode("000007");
        verify(sender).send(anyString(), eq(EMAIL), anyString(), contains("000007"));
    }

    @Test
    void unknownAndSuspendedAddressesUseTheSamePersistedBudgetsWithoutSending() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> service.start(EMAIL));
        assertNull(stored.getBoundUserId());
        assertEquals(1, stored.getSendCount());
        user.suspend();
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        clock.advance(Duration.ofSeconds(60));
        assertDoesNotThrow(() -> service.start(EMAIL));
        assertNull(stored.getBoundUserId());
        assertEquals(2, stored.getSendCount());
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void adminRecoveryIsGenericButCannotSendMailOrChangeThePrivateConfiguredPassword() {
        user.makeAdmin();
        assertDoesNotThrow(() -> service.start(EMAIL));
        assertNull(stored.getBoundUserId());
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        assertEquals(UserRole.ADMIN, user.getRole());
        assertEquals("old-password-hash", user.getPasswordHash());
        assertEquals(0, user.getAuthVersion());
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void accountPromotedToAdminAfterIssueCannotUseTheOldPublicRecoveryCode() {
        service.start(EMAIL);
        user.makeAdmin();
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        assertEquals("old-password-hash", user.getPasswordHash());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void missingSenderAndDeliveryFailureDoNotRevealAccountPresence() {
        when(sender.isConfigured()).thenReturn(false);
        assertDoesNotThrow(() -> service.start(EMAIL));
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
        clock.advance(Duration.ofSeconds(60));
        when(sender.isConfigured()).thenReturn(true);
        doThrow(new EmailDeliveryException()).when(sender).send(anyString(), anyString(), anyString(), anyString());
        assertDoesNotThrow(() -> service.start(EMAIL));
        assertEquals(2, stored.getSendCount());
        assertEquals("old-password-hash", user.getPasswordHash());
    }

    @Test
    void cooldownAndHourlySendLimitSilentlySuppressExtraMail() {
        service.start(EMAIL);
        service.start(EMAIL);
        assertEquals(1, stored.getSendCount());
        for (int send = 1; send < 7; send++) {
            clock.advance(Duration.ofSeconds(60));
            assertDoesNotThrow(() -> service.start(EMAIL));
        }
        assertEquals(5, stored.getSendCount());
        verify(sender, times(5)).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void successfulFinishChangesPasswordAndCredentialVersionThenConsumesCodeOnce() {
        service.start(EMAIL);
        service.finish("FARMER@EXAMPLE.COM", "123456", NEW_PASSWORD);
        assertEquals("new-password-hash", user.getPasswordHash());
        assertEquals(1, user.getAuthVersion());
        assertEquals(clock.instant(), stored.getConsumedAt());
        assertThrows(InvalidRecoveryCodeException.class, () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        verify(users, times(1)).saveAndFlush(user);
        var sequence = inOrder(recoveries, users);
        sequence.verify(recoveries).findByEmailForUpdate(EMAIL);
        sequence.verify(recoveries).findByEmailForUpdate(EMAIL);
        sequence.verify(users).findByIdForUpdate(7L);
        sequence.verify(users).saveAndFlush(user);
    }

    @Test
    void expiredAndMissingRecoveryHaveTheSameErrorAndCannotChangePassword() {
        var missing = assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        service.start(EMAIL);
        clock.advance(Duration.ofMinutes(10));
        var expired = assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        assertEquals(missing.getReason(), expired.getReason());
        assertEquals("old-password-hash", user.getPasswordHash());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void fiveWrongAttemptsPersistAndCannotBeResetByResending() {
        service.start(EMAIL);
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(InvalidRecoveryCodeException.class,
                    () -> service.finish(EMAIL, "654321", NEW_PASSWORD));
        }
        assertEquals(5, stored.getFailedAttempts());
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        clock.advance(Duration.ofSeconds(60));
        assertDoesNotThrow(() -> service.start(EMAIL));
        assertEquals(1, stored.getSendCount());
        assertEquals(5, stored.getFailedAttempts());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void resendChangesCodeWhilePreservingFailedAttemptCount() {
        service.start(EMAIL);
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "654321", NEW_PASSWORD));
        clock.advance(Duration.ofSeconds(60));
        when(random.nextInt(1_000_000)).thenReturn(654321);
        when(encoder.encode("654321")).thenReturn("new-code-hash");
        when(encoder.matches("123456", "new-code-hash")).thenReturn(false);
        when(encoder.matches("654321", "new-code-hash")).thenReturn(true);
        service.start(EMAIL);
        assertEquals(1, stored.getFailedAttempts());
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        service.finish(EMAIL, "654321", NEW_PASSWORD);
        assertEquals(2, stored.getFailedAttempts());
    }

    @Test
    void onlyTheExpiredHourRefreshesAttemptAndSendBudgets() {
        service.start(EMAIL);
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "654321", NEW_PASSWORD));
        clock.advance(Duration.ofMinutes(59));
        service.start(EMAIL);
        assertEquals(1, stored.getFailedAttempts());
        assertEquals(2, stored.getSendCount());
        clock.advance(Duration.ofMinutes(1));
        service.start(EMAIL);
        assertEquals(0, stored.getFailedAttempts());
        assertEquals(1, stored.getSendCount());
    }

    @Test
    void oldCodeCannotOverwriteAnotherPasswordChangeOrSuspension() {
        service.start(EMAIL);
        user.changePasswordHash("another-new-hash");
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        assertEquals("another-new-hash", user.getPasswordHash());
        clock.advance(Duration.ofSeconds(60));
        service.start(EMAIL);
        user.suspend();
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void deletionAfterCodeIssueAndUnknownAddressCannotCreateAnAccount() {
        service.start(EMAIL);
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "123456", NEW_PASSWORD));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void malformedCodesConsumeAnAttemptButPasswordLengthIsCheckedFirst() {
        service.start(EMAIL);
        var badPassword = assertThrows(ResponseStatusException.class,
                () -> service.finish(EMAIL, "123456", "too short"));
        assertEquals(HttpStatus.BAD_REQUEST, badPassword.getStatusCode());
        assertEquals(0, stored.getFailedAttempts());
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(EMAIL, "not-a-code", NEW_PASSWORD));
        assertEquals(1, stored.getFailedAttempts());
    }

    @Test
    void onlyInvalidCodeErrorsCommitAttemptsAndOtherFailuresRollbackBothWrites() throws Exception {
        var attribute = new AnnotationTransactionAttributeSource().getTransactionAttribute(
                PasswordRecoveryService.class.getMethod("finish", String.class, String.class, String.class),
                PasswordRecoveryService.class);
        assertNotNull(attribute);
        assertFalse(attribute.rollbackOn(new InvalidRecoveryCodeException()));
        assertTrue(attribute.rollbackOn(new IllegalStateException("database failure")));
        assertTrue(attribute.rollbackOn(new ResponseStatusException(HttpStatus.BAD_REQUEST)));
        var lock = PasswordRecoveryRepository.class.getMethod("findByEmailForUpdate", String.class)
                .getAnnotation(org.springframework.data.jpa.repository.Lock.class);
        assertEquals(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE, lock.value());
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
