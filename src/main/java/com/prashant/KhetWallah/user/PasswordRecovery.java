package com.prashant.KhetWallah.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "password_recoveries")
public class PasswordRecovery {
    @Id
    @Column(nullable = false, length = 254)
    private String email;
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
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "bound_user_id")
    private Long boundUserId;
    @Column(name = "issued_auth_version")
    private Long issuedAuthVersion;

    protected PasswordRecovery() {}

    PasswordRecovery(String email, Instant now) {
        this.email = email;
        this.codeHash = "";
        this.expiresAt = now;
        this.resendAt = now;
        this.windowStartedAt = now;
    }

    void refreshWindow(Instant now) {
        if (!now.isBefore(windowStartedAt.plus(Duration.ofHours(1)))) {
            windowStartedAt = now;
            sendCount = 0;
            failedAttempts = 0;
        }
    }

    void issue(String hash, Instant now, AppUser user) {
        codeHash = hash;
        expiresAt = now.plusSeconds(600);
        resendAt = now.plusSeconds(60);
        consumedAt = null;
        boundUserId = user == null ? null : user.getId();
        issuedAuthVersion = user == null ? null : user.getAuthVersion();
        sendCount++;
    }

    void fail() { failedAttempts++; }
    void consume(Instant now) { consumedAt = now; }
    public String getEmail() { return email; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getResendAt() { return resendAt; }
    public int getSendCount() { return sendCount; }
    public int getFailedAttempts() { return failedAttempts; }
    public Instant getConsumedAt() { return consumedAt; }
    public Long getBoundUserId() { return boundUserId; }
    public Long getIssuedAuthVersion() { return issuedAuthVersion; }

    @Override public String toString() { return "PasswordRecovery[REDACTED]"; }
}
