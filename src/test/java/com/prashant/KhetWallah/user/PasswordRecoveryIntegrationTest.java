package com.prashant.KhetWallah.user;

import com.prashant.KhetWallah.mail.EmailSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses only unique synthetic accounts; finally cleanup commits because service transactions must be tested. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "app.email.enabled=false"})
class PasswordRecoveryIntegrationTest {
    private static final String OLD_PASSWORD = "Original integration passphrase 2026!";
    private static final String NEW_PASSWORD = "Replacement integration passphrase 2026!";
    @Autowired PasswordRecoveryService service;
    @Autowired PasswordRecoveryRepository recoveries;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailSender sender;
    @MockitoBean com.prashant.KhetWallah.mail.EmailOutboxWorker outboxWorker;
    private TransactionTemplate transaction;
    private String email;
    private Long userId;
    private final AtomicReference<String> deliveredCode = new AtomicReference<>();

    @BeforeEach
    void prepareSyntheticAccountAndInMemorySender() {
        transaction = new TransactionTemplate(transactions);
        email = "recovery-" + UUID.randomUUID() + "@example.test";
        userId = transaction.execute(status -> users.saveAndFlush(
                new AppUser("Recovery integration test", email, encoder.encode(OLD_PASSWORD))).getId());
        when(sender.isConfigured()).thenReturn(true);
        doAnswer(invocation -> {
            assertEquals(email, invocation.getArgument(1));
            var matcher = Pattern.compile("code is: ([0-9]{6})").matcher((String) invocation.getArgument(3));
            assertTrue(matcher.find());
            deliveredCode.set(matcher.group(1));
            return null;
        }).when(sender).send(anyString(), anyString(), anyString(), anyString());
    }

    @AfterEach
    void removeOnlyThisTestsSyntheticRecords() {
        if (email != null && transaction != null) transaction.executeWithoutResult(status -> {
            recoveries.deleteById(email);
            recoveries.flush();
            if (userId != null) users.deleteById(userId);
            users.flush();
        });
    }

    @Test
    void mysqlUpsertHashSingleUseAndPasswordGenerationWorkAcrossCommittedTransactions() {
        service.start(email);
        String code = deliveredCode.get();
        assertNotNull(code);
        PasswordRecovery challenge = transaction.execute(status -> recoveries.findById(email).orElseThrow());
        assertNotEquals(code, challenge.getCodeHash());
        assertTrue(encoder.matches(code, challenge.getCodeHash()));
        service.finish(email, code, NEW_PASSWORD);
        AppUser changed = transaction.execute(status -> users.findById(userId).orElseThrow());
        assertTrue(encoder.matches(NEW_PASSWORD, changed.getPasswordHash()));
        assertFalse(encoder.matches(OLD_PASSWORD, changed.getPasswordHash()));
        assertEquals(1L, changed.getAuthVersion());
        assertNotNull(transaction.execute(status -> recoveries.findById(email).orElseThrow().getConsumedAt()));
        assertThrows(InvalidRecoveryCodeException.class, () -> service.finish(email, code, OLD_PASSWORD));
        assertEquals(1L, (long) transaction.execute(status -> users.findById(userId).orElseThrow().getAuthVersion()));
    }

    @Test
    void wrongAttemptsCommitDespiteTheThrownExceptionAndResendCannotClearThem() {
        service.start(email);
        String code = deliveredCode.get();
        String wrong = "000000".equals(code) ? "111111" : "000000";
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(InvalidRecoveryCodeException.class, () -> service.finish(email, wrong, NEW_PASSWORD));
            assertEquals(attempt, (int) transaction.execute(status -> recoveries.findById(email).orElseThrow().getFailedAttempts()));
        }
        jdbc.update("UPDATE password_recoveries SET resend_at = ? WHERE email = ?",
                Timestamp.from(Instant.now().minusSeconds(120)), email);
        service.start(email);
        assertEquals(5, (int) transaction.execute(status -> recoveries.findById(email).orElseThrow().getFailedAttempts()));
        assertEquals(1, (int) transaction.execute(status -> recoveries.findById(email).orElseThrow().getSendCount()));
        assertThrows(InvalidRecoveryCodeException.class, () -> service.finish(email, code, NEW_PASSWORD));
        assertTrue(encoder.matches(OLD_PASSWORD,
                transaction.execute(status -> users.findById(userId).orElseThrow().getPasswordHash())));
        verify(sender, times(1)).send(anyString(), eq(email), anyString(), anyString());
    }

    @Test
    void expiredProofDoesNotChangeTheAccount() {
        service.start(email);
        jdbc.update("UPDATE password_recoveries SET expires_at = ? WHERE email = ?",
                Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")), email);
        assertTrue(transaction.execute(status -> recoveries.findById(email).orElseThrow().getExpiresAt()).isBefore(Instant.now()));
        assertThrows(InvalidRecoveryCodeException.class,
                () -> service.finish(email, deliveredCode.get(), NEW_PASSWORD));
        assertEquals(0L, (long) transaction.execute(status -> users.findById(userId).orElseThrow().getAuthVersion()));
        assertNull(transaction.execute(status -> recoveries.findById(email).orElseThrow().getConsumedAt()));
    }

    @Test
    void simultaneousFirstRequestsCreateOneChallengeAndSendOneCode() throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); service.start(email); return true; });
            var second = executor.submit(() -> { gate.await(); service.start(email); return true; });
            gate.countDown();
            assertTrue(first.get(20, TimeUnit.SECONDS));
            assertTrue(second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, (int) transaction.execute(status -> recoveries.findById(email).orElseThrow().getSendCount()));
        verify(sender, times(1)).send(anyString(), eq(email), anyString(), anyString());
    }
}
