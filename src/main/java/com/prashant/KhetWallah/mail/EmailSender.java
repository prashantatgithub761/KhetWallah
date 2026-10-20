package com.prashant.KhetWallah.mail;

public interface EmailSender {
    boolean isConfigured();

    /** Returns when the provider accepts the message; this does not confirm inbox delivery. */
    void send(String idempotencyKey, String recipient, String subject, String body);
}
