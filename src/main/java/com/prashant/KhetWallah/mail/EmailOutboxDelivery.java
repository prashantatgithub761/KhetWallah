package com.prashant.KhetWallah.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class EmailOutboxDelivery {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxDelivery.class);
    private final EmailOutboxRepository repository;
    private final EmailSender sender;
    private final Clock clock;

    @Autowired
    public EmailOutboxDelivery(EmailOutboxRepository repository, EmailSender sender) {
        this(repository, sender, Clock.systemUTC());
    }

    EmailOutboxDelivery(EmailOutboxRepository repository, EmailSender sender, Clock clock) {
        this.repository = repository;
        this.sender = sender;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(String id) {
        if (!sender.isConfigured()) {
            return;
        }
        EmailOutbox message = repository.findByIdForUpdate(id).orElse(null);
        Instant now = clock.instant();
        if (message == null || !message.isDue(now)) {
            return;
        }
        message.markAttempt();
        try {
            sender.send("outbox-" + message.getId(), message.getRecipient(),
                    message.getSubject(), message.getBody());
            message.markSent(clock.instant());
        } catch (RuntimeException ignored) {
            message.markFailure(clock.instant());
            log.warn("Email delivery failed for outbox {}", message.getId());
        }
        // The locked, managed row is persisted by JPA when this transaction commits.
    }
}
