package com.prashant.KhetWallah.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailQueueTest {
    @Test
    void unsupportedLegacyAddressDoesNotBreakTheOrderTransaction() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(sender.isConfigured()).thenReturn(true);
        assertDoesNotThrow(() -> new EmailQueue(repository, sender).enqueue(
                "unsupported@example", "Order update", "Details"));
        verifyNoInteractions(repository);
    }

    @Test
    void configuredQueueCreatesDurableMessageWithAnImmediateFirstAttempt() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(sender.isConfigured()).thenReturn(true);

        new EmailQueue(repository, sender).enqueue("buyer@example.com", "Order placed", "Order details");

        ArgumentCaptor<EmailOutbox> captor = ArgumentCaptor.forClass(EmailOutbox.class);
        verify(repository).save(captor.capture());
        EmailOutbox message = captor.getValue();
        assertEquals(message.getId(), UUID.fromString(message.getId()).toString());
        assertEquals("buyer@example.com", message.getRecipient());
        assertEquals("Order placed", message.getSubject());
        assertEquals("Order details", message.getBody());
        assertEquals(message.getCreatedAt(), message.getNextAttemptAt());
        assertEquals(0, message.getAttempts());
        assertNull(message.getSentAt());
        assertFalse(message.isFailed());
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void disabledQueueSkipsPersistence() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);

        new EmailQueue(repository, sender).enqueue("buyer@example.com", "Order placed", "Body");

        verifyNoInteractions(repository);
    }

    @Test
    void springProxyRequiresAnExistingTransactionAndJoinsTheCallersTransaction() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(sender.isConfigured()).thenReturn(true);
        RecordingTransactionManager transactions = new RecordingTransactionManager();
        ProxyFactory proxyFactory = new ProxyFactory(new EmailQueue(repository, sender));
        proxyFactory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        EmailQueue queue = (EmailQueue) proxyFactory.getProxy();

        assertThrows(IllegalTransactionStateException.class,
                () -> queue.enqueue("buyer@example.com", "Order placed", "Body"));
        verifyNoInteractions(repository);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            queue.enqueue("buyer@example.com", "Order placed", "Body");
            assertEquals(1, transactions.begins);
            assertEquals(0, transactions.commits, "Enqueue must not commit independently");
        });

        verify(repository).save(any(EmailOutbox.class));
        assertEquals(1, transactions.begins);
        assertEquals(1, transactions.commits);
    }

    private static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean active;
        private int begins;
        private int commits;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object transaction) { return active; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            active = true;
            begins++;
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
        @Override protected void doCleanupAfterCompletion(Object transaction) { active = false; }
    }
}
