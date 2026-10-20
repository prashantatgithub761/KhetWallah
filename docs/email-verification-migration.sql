-- Run once on the intended KhetWallah MySQL database before this signup upgrade.
-- Back up the database first. Timestamp values are UTC, with microsecond precision.
-- Keep rows after code expiry: deleting unconsumed rows early would erase hourly limits.
CREATE TABLE email_verifications (
    email varchar(254) NOT NULL PRIMARY KEY,
    challenge_id varchar(36) NOT NULL,
    code_hash varchar(255) NOT NULL,
    expires_at datetime(6) NOT NULL,
    resend_at datetime(6) NOT NULL,
    window_started_at datetime(6) NOT NULL,
    send_count int NOT NULL,
    failed_attempts int NOT NULL,
    CONSTRAINT uk_email_verifications_challenge UNIQUE (challenge_id)
);

-- Only normalized email, a random challenge ID, a one-way code hash, timestamps,
-- and counters are stored. Account name/password are supplied only at final signup.
-- A transaction locks the email row through verification consumption and registration.
-- Wrong-code transactions commit only the failed-attempt increment; mail failures roll back.
-- A first-insert race is rejected with HTTP 409 using the primary key/locking guard.
--
-- VerificationRateLimiter adds at most 30 requests/IP/hour with 10,000 live keys.
-- It is in memory on one application instance and resets on process restart.
-- Controllers must use request.getRemoteAddr(), never untrusted forwarding headers.
-- Behind a proxy this may be the proxy address: configure a trusted proxy boundary,
-- or apply shared ingress limits before deploying multiple application instances.
