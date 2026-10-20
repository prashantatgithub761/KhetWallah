package com.prashant.KhetWallah.mail;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailOutboxWorkerTest {
    @Test
    void disabledWorkerDoesNotScanOrProcessRows() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        EmailOutboxDelivery delivery = mock(EmailOutboxDelivery.class);

        new EmailOutboxWorker(repository, sender, delivery).sendDueEmails();

        verifyNoInteractions(repository, delivery);
    }

    @Test
    void workerProcessesAtMostTenDueRowsAndContinuesAfterOneTransactionFails() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailSender sender = mock(EmailSender.class);
        EmailOutboxDelivery delivery = mock(EmailOutboxDelivery.class);
        when(sender.isConfigured()).thenReturn(true);
        when(repository.findDueIds(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of("first-id", "second-id"));
        doThrow(new IllegalStateException("Private database diagnostics"))
                .when(delivery).deliver("first-id");

        new EmailOutboxWorker(repository, sender, delivery).sendDueEmails();

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findDueIds(any(Instant.class), page.capture());
        assertEquals(10, page.getValue().getPageSize());
        assertEquals(0, page.getValue().getPageNumber());
        verify(delivery).deliver("first-id");
        verify(delivery).deliver("second-id");
    }
}
