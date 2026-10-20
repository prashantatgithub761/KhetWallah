package com.prashant.KhetWallah.mail;

import java.util.regex.Pattern;

final class EmailContent {
    private static final Pattern MAILBOX = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$"
    );

    private EmailContent() {
    }

    static boolean isMailbox(String value) {
        return value != null && value.length() <= 254 && MAILBOX.matcher(value).matches();
    }

    static boolean isFrom(String value) {
        if (value == null || hasLineBreak(value)) {
            return false;
        }
        if (isMailbox(value)) {
            return true;
        }
        int opening = value.indexOf('<');
        return opening > 0 && value.endsWith(">")
                && !value.substring(0, opening).isBlank()
                && value.substring(0, opening).indexOf('>') < 0
                && isMailbox(value.substring(opening + 1, value.length() - 1));
    }

    static boolean hasLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }

    static void validate(String recipient, String subject, String body) {
        if (!isMailbox(recipient) || subject == null || subject.isBlank()
                || subject.length() > 200 || hasLineBreak(subject)
                || body == null || body.isBlank()) {
            throw new EmailDeliveryException();
        }
    }
}
