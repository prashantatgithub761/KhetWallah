package com.prashant.KhetWallah.mail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox", indexes = @Index(name = "idx_email_outbox_due",
        columnList = "failed,sent_at,next_attempt_at"))
public class EmailOutbox {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 254, updatable = false)
    private String recipient;

    @Column(nullable = false, length = 200, updatable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private boolean failed;

    protected EmailOutbox() {
        this.id = UUID.randomUUID().toString();
    }

    public EmailOutbox(String recipient, String subject, String body) {
        this(recipient, subject, body, Instant.now());
    }

    EmailOutbox(String recipient, String subject, String body, Instant createdAt) {
        this();
        EmailContent.validate(recipient, subject, body);
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    boolean isDue(Instant now) {
        return sentAt == null && !failed && !nextAttemptAt.isAfter(now);
    }

    void markAttempt() {
        attempts++;
    }

    void markSent(Instant now) {
        sentAt = now;
    }

    void markFailure(Instant now) {
        if (attempts >= 5) {
            failed = true;
        } else {
            nextAttemptAt = now.plusSeconds(60L << Math.min(attempts - 1, 4));
        }
    }

    public String getId() { return id; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }
    public boolean isFailed() { return failed; }
}
