package com.prashant.KhetWallah.mail;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailOutboxDeliveryTest {
    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    void successfulDeliveryRecordsAcceptanceAndSkipsAlreadySentMessages() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        EmailOutbox message = new EmailOutbox("buyer@example.com", "Order accepted", "Order body", NOW);
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findByIdForUpdate(message.getId())).thenReturn(Optional.of(message));
        EmailOutboxDelivery delivery = delivery(repository, sender, NOW);

        delivery.deliver(message.getId());
        delivery.deliver(message.getId());

        verify(sender, times(1)).send("outbox-" + message.getId(),
                "buyer@example.com", "Order accepted", "Order body");
        assertEquals(1, message.getAttempts());
        assertEquals(NOW, message.getSentAt());
        assertFalse(message.isFailed());
    }

    @Test
    void disabledDeliveryLeavesRowsUntouchedWithoutLockingOrSending() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);

        delivery(repository, sender, NOW).deliver("id");

        verifyNoInteractions(repository);
        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void retryUsesStableKeyAndStopsAfterFiveAttemptsWithBoundedBackoff() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        EmailOutbox message = new EmailOutbox("buyer@example.com", "Subject", "Body", NOW);
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findByIdForUpdate(message.getId())).thenReturn(Optional.of(message));
        doThrow(new EmailDeliveryException()).when(sender)
                .send(anyString(), anyString(), anyString(), anyString());
        Instant attemptAt = NOW;

        for (int attempt = 1; attempt <= 5; attempt++) {
            delivery(repository, sender, attemptAt).deliver(message.getId());
            assertEquals(attempt, message.getAttempts());
            assertNull(message.getSentAt());
            if (attempt < 5) {
                assertFalse(message.isFailed());
                assertEquals(attemptAt.plusSeconds(60L << (attempt - 1)), message.getNextAttemptAt());
                delivery(repository, sender, attemptAt).deliver(message.getId());
                assertEquals(attempt, message.getAttempts(), "Future retry must be skipped");
                attemptAt = message.getNextAttemptAt();
            }
        }

        assertTrue(message.isFailed());
        delivery(repository, sender, attemptAt.plusSeconds(86_400)).deliver(message.getId());
        assertEquals(5, message.getAttempts());
        verify(sender, times(5)).send("outbox-" + message.getId(), "buyer@example.com", "Subject", "Body");
    }

    @Test
    void successfulRetryPreservesAttemptsAndRecordsAcceptance() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        EmailOutbox message = new EmailOutbox("buyer@example.com", "Subject", "Body", NOW);
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findByIdForUpdate(message.getId())).thenReturn(Optional.of(message));
        doThrow(new EmailDeliveryException()).doNothing().when(sender)
                .send(anyString(), anyString(), anyString(), anyString());

        delivery(repository, sender, NOW).deliver(message.getId());
        delivery(repository, sender, NOW.plusSeconds(60)).deliver(message.getId());

        assertEquals(2, message.getAttempts());
        assertEquals(NOW.plusSeconds(60), message.getSentAt());
        assertFalse(message.isFailed());
    }

    @Test
    void missingRowsDoNotSend() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findByIdForUpdate("missing")).thenReturn(Optional.empty());

        delivery(repository, sender, NOW).deliver("missing");

        verify(sender, never()).send(anyString(), anyString(), anyString(), anyString());
    }

    private EmailOutboxDelivery delivery(EmailOutboxRepository repository, EmailSender sender, Instant now) {
        return new EmailOutboxDelivery(repository, sender, Clock.fixed(now, ZoneOffset.UTC));
    }
}
