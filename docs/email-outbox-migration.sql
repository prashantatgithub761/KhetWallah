-- MySQL 8: apply once before deploying with spring.jpa.hibernate.ddl-auto=validate.
CREATE TABLE email_outbox (
    id VARCHAR(36) NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    failed BIT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_email_outbox_due (failed, sent_at, next_attempt_at)
);

-- Configuration: app.email.enabled, app.email.resend-api-key, app.email.from.
-- Defaults disable sending. Keep credentials in environment/secret storage.
-- The from mailbox must belong to a sender/domain authorized in Resend.
-- Order messages are committed in the same transaction as the order change.
-- A worker starts after 10 seconds, polls every 60 seconds and processes up to
-- 10 due rows, each in its own transaction under a pessimistic write lock.
-- At most 5 attempts are made, with 60/120/240/480-second retry delays.
-- Failed rows stay stored for operator inspection; no automatic reset occurs.
-- Disabled/unconfigured email skips new enqueue operations and pauses the worker.
-- Enabling it later resumes any previously queued, due messages.
-- sent_at means Resend accepted the API request. Inbox delivery is not confirmed;
-- this application does not consume delivery/bounce webhooks.
-- Stable per-row Idempotency-Key values reduce duplication after a crash between
-- provider acceptance and database commit. Resend retains these keys for 24 hours;
-- a crash followed by downtime beyond that window can still cause duplicates.
-- Treat recipient/body fields as private data and apply a suitable retention policy.
-- Provider reference: https://resend.com/docs/api-reference/emails/send-email
-- Idempotency reference: https://resend.com/docs/dashboard/emails/idempotency-keys
