package com.prashant.KhetWallah.mail;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailQueue {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailQueue.class);
    private final EmailOutboxRepository repository;
    private final EmailSender sender;

    public EmailQueue(EmailOutboxRepository repository, EmailSender sender) {
        this.repository = repository;
        this.sender = sender;
    }

    /** The row commits or rolls back with the order mutation that queued it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String recipient, String subject, String body) {
        if (sender.isConfigured()) {
            EmailOutbox message;
            try {
                message = new EmailOutbox(recipient, subject, body);
            } catch (EmailDeliveryException invalidContent) {
                // Legacy accounts can contain addresses unsupported by the mail provider.
                // Skip this recipient without marking the order transaction rollback-only.
                log.warn("Skipped an email notification with unsupported message fields");
                return;
            }
            repository.save(message);
        }
    }
}
