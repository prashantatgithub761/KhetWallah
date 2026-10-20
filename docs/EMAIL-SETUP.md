# Email OTP and order notifications

New registrations now need a six-digit code sent to the submitted email address.
An account is created only when the correct, unexpired code and the final account
information are submitted together. Existing accounts and password login continue
working. No OTP is required for every login.

## 1. Add the database tables before restarting

The project uses `ddl-auto=validate`: both new tables must exist even when sending
is disabled. Back up the app's database first. The default local database name is
`KhetWallah`; if your `DATABASE_URL` selects a different database, use that one.

In Mac Terminal, connect using your actual MySQL account:

```sh
mysql -u root -p
```

At the `mysql>` prompt, run these commands individually:

```sql
USE KhetWallah;
SOURCE /Users/prashantchoudhary/Desktop/KhetWallah/docs/email-verification-migration.sql
SOURCE /Users/prashantchoudhary/Desktop/KhetWallah/docs/email-outbox-migration.sql
SHOW TABLES LIKE 'email_%';
```

Each migration is intended to run once. If a table already exists, inspect its
columns rather than recreating or dropping it. Apply these migrations separately
to the Railway database before deploying the new version there.

## 2. Start with the local test inbox (no domain, provider, or credentials)

In a separate Mac Terminal, run:

```sh
cd /Users/prashantchoudhary/Desktop/KhetWallah
python3 docs/test-email-server.py
```

Keep it open. Visit **http://127.0.0.1:8025/** to view the inbox. It accepts mail
only from the local test sender, keeps at most 100 messages in memory, and sends
nothing externally. Restarting this helper clears its messages.

In IntelliJ, open **Run → Edit Configurations → KhetWallahApplication** and add
this environment variable beside your existing database variables:

```text
SPRING_PROFILES_ACTIVE=email-test
```

Keep your database variables. Rebuild and restart the application. This profile
selects a sender that calls the fixed loopback inbox instead of a real provider;
no `RESEND_API_KEY`, `EMAIL_FROM`, or `EMAIL_ENABLED` is needed for local testing.

1. Open **http://localhost:8080/register.html**.
2. Enter a new test email address, name, and matching password; select
   **Send verification code**.
3. Refresh the local inbox and copy its six-digit code.
4. Enter the code and select **Verify email and create account**.
5. Sign in. Existing accounts can still sign in directly.
6. Use separate buyer and farmer sessions to place/accept/reject an order,
   update pickup, cancel a pending order, and confirm collection. Emails for both
   parties will appear in the test inbox. Order delivery polls every 60 seconds,
   so wait up to a minute and refresh it. Signup codes are sent immediately.

Wrong codes do not create accounts. Codes expire in ten minutes. Resends have a
60-second cooldown; all starts/resends for an email share a five-send/hour limit.
Five wrong codes lock that email's verification attempts for the remainder of
its hourly window; resending does not reset the count. Only the newest code works.

Do not set the `email-test` profile on a public deployment. Its fixed loopback
inbox exists only on your Mac; the Railway app would have a different loopback.
The helper displays sensitive test messages locally, so use sample accounts.

## 3. Enable real email later

Remove `email-test` from active profiles and configure the following **private
variables on the application service**, not the database service:

| Variable | Value |
| --- | --- |
| `EMAIL_ENABLED` | `true` |
| `RESEND_API_KEY` | Your private Resend sending API key |
| `EMAIL_FROM` | A sender allowed by Resend, such as `KhetWallah <notifications@your-domain>` |
| `APP_PUBLIC_URL` | Your application's actual HTTPS origin, with no path or query |

These are descriptions, not literal credentials to copy. Keep keys out of Git,
chat, browser JavaScript, and screenshots. Resend's testing `resend.dev` sender can
send only to the account owner's email. Sending to arbitrary users requires an
authorized sender/domain. Check its current plan and verification requirements
before enabling it for real users.

- API documentation: https://resend.com/docs/api-reference/emails/send-email
- Testing sender restriction: https://resend.com/docs/knowledge-base/403-error-resend-dev-domain

The application uses HTTPS for mail delivery. In the normal profile, delivery
is disabled by default. If not configured, existing account/order functions
continue, no new order emails are queued, and new signup code requests return a
clear HTTP 503 rather than bypassing verification.

## Delivery and reliability

Messages cover new requests, acceptance, rejection, pending cancellation, pickup
changes, and buyer-confirmed completion. Each party receives a separate message.
Pickup emails link to live order details, so an older delayed email does not
repeat an obsolete meeting time or location. Order emails are snapshots of the
event recorded time, not payment receipts.

Order updates and their email queue entries commit together. Remote delivery runs
later and cannot roll back a committed order. A failure retries up to five total
attempts (60/120/240/480-second delays); after that the row stays marked `failed`.
Repeated accept/reject/cancel/complete requests do not generate more emails after
an order has already reached that state. Identical pickup details do not requeue.

`email_outbox.sent_at` means the provider accepted the message, not that it reached
the inbox. Bounces/spam/delivery webhooks are not implemented. Stable per-message
idempotency keys reduce duplicate delivery; Resend retains keys for 24 hours.
A provider-acceptance/DB-commit crash plus longer downtime can still duplicate a
message. Free hosts may sleep and delay the background worker.

Operators can inspect queue health using these queries in the intended database:

```sql
SELECT COUNT(*) AS waiting FROM email_outbox WHERE sent_at IS NULL AND failed = 0;
SELECT id, attempts, next_attempt_at FROM email_outbox WHERE failed = 1;
```

Email bodies and recipients are private data. Define retention for sent/failed
rows and remove abandoned verification rows only after their hourly rate-limit
windows have elapsed. No automatic data cleanup or admin retry UI is included.

The additional in-memory IP limiter permits 30 signup requests per hour on one
app instance. Behind a shared proxy it may group clients; use trusted ingress
limits before running multiple instances. The database's per-email limits
persist across application restarts.


## Repeated registration redirect / connection message

A running Java process keeps the classes it loaded at startup. Rebuilding can
update static pages while the old server still uses the old API. If the new OTP
page repeatedly redirects to sign-in, use this local runner from Mac Terminal:

```sh
cd /Users/prashantchoudhary/Desktop/KhetWallah
python3 docs/run-email-test.py
```

It reuses the private database settings saved in IntelliJ's local
`KhetWallahApplication` run configuration. It never prints credential values.
It checks that MySQL is local, builds the JAR first, gracefully stops only your
identified KhetWallah Java server on port 8080, and starts the updated app with
`email-test`. It reuses an existing local inbox or starts one if needed.

Keep this Terminal open and wait for the Spring startup message before trying
registration again. Use Ctrl+C in it before starting the app from IntelliJ.
The runner makes no schema changes, so the two tables above are still required.
For a read-only readiness check, add `--check` to the runner command.
