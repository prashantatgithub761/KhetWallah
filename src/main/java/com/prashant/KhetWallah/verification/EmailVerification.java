package com.prashant.KhetWallah.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "email_verifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_email_verifications_challenge", columnNames = "challenge_id"))
public class EmailVerification {
    @Id
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "challenge_id", nullable = false, length = 36)
    private String challengeId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_at", nullable = false)
    private Instant resendAt;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    protected EmailVerification() {}

    EmailVerification(String email, Instant now) {
        this.email = email;
        this.windowStartedAt = now;
    }

    void refreshWindow(Instant now, Duration window) {
        if (!now.isBefore(windowStartedAt.plus(window))) {
            windowStartedAt = now;
            sendCount = 0;
            failedAttempts = 0;
        }
    }

    void issueCode(String challengeId, String codeHash, Instant expiresAt, Instant resendAt) {
        this.challengeId = challengeId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.resendAt = resendAt;
        sendCount++;
    }

    void recordFailedAttempt() {
        failedAttempts++;
    }

    public String getEmail() { return email; }
    public String getChallengeId() { return challengeId; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getResendAt() { return resendAt; }
    public Instant getWindowStartedAt() { return windowStartedAt; }
    public int getSendCount() { return sendCount; }
    public int getFailedAttempts() { return failedAttempts; }

    @Override
    public String toString() {
        return "EmailVerification[REDACTED]";
    }
}
