package com.prashant.KhetWallah.mail;

/** Safe to surface to callers: provider responses, message contents and credentials are omitted. */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException() {
        super("Email delivery is temporarily unavailable");
    }
}
