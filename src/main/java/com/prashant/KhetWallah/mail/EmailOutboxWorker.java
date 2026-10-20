package com.prashant.KhetWallah.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class EmailOutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxWorker.class);
    private final EmailOutboxRepository repository;
    private final EmailSender sender;
    private final EmailOutboxDelivery delivery;

    public EmailOutboxWorker(EmailOutboxRepository repository, EmailSender sender,
                            EmailOutboxDelivery delivery) {
        this.repository = repository;
        this.sender = sender;
        this.delivery = delivery;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void sendDueEmails() {
        if (!sender.isConfigured()) {
            return;
        }
        for (String id : repository.findDueIds(Instant.now(), PageRequest.of(0, 10))) {
            try {
                // Calling a separate bean ensures REQUIRES_NEW is applied to every row.
                delivery.deliver(id);
            } catch (RuntimeException ignored) {
                log.warn("Email outbox processing failed for {}", id);
            }
        }
    }
}
