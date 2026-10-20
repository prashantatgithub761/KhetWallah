# Password recovery

Open `/recovery.html` or use **Forgot password** on the sign-in page. Enter the
registered email, then the most recent six-digit email code and a new password
of 15–128 characters. A successful reset returns the user to sign-in; it does
not automatically authenticate the browser.

## Account policy

- Regular, active accounts may recover access through their registered email.
- Unknown emails, suspended accounts, and administrator accounts receive the
  same generic request response, but no recovery email is sent to them.
- Administrators rotate their password through the private `ADMIN_PASSWORD`
  deployment variable and restart the application. `AdminAccountBootstrap`
  intentionally keeps that configuration authoritative; public email recovery
  cannot change an administrator password.
- The account, listings, and orders remain intact. Resetting a regular account
  increments its credential version. Previous authenticated sessions lose
  access on their next request and must sign in again.

## Email setup and local testing

The recovery flow uses the existing `EmailSender`. Local testing uses the
`email-test` profile and the existing inbox at `http://127.0.0.1:8025`; it does
not send real messages. Do not enable the test profile on a public deployment.
For public delivery, configure the real email sender as described in
`EMAIL-SETUP.md`. A generic request response means only that the request was
received; it cannot confirm whether an account exists or a message arrived.
Mail-delivery failures deliberately produce that same generic response.

## Code and transaction behaviour

Only a password hash of the code is stored in `password_recoveries`. Codes
expire after 10 minutes, are replaced on resend, and can be consumed once.
Resends require at least 60 seconds. Each normalized email has a persistent
maximum of five sends and five wrong-code attempts per hour. Resending or
successfully consuming a code does not clear its hourly budgets. A separate
per-instance IP limiter adds a maximum of 30 recovery requests per hour.

Code consumption and password replacement commit in the same transaction under
recovery-row and account locks. Wrong attempts are deliberately committed even
when an invalid-code exception is returned. A reset issued before a password
change, account suspension, or promotion to administrator cannot overwrite that
later account state. Both POST endpoints retain CSRF protection, and responses
do not include the code, password, account status, or email-delivery details.

Apply the combined additive database migration before running with schema
validation. It adds `password_recoveries` and `app_users.auth_version` while
retaining existing users and their passwords.

## Verification

`PasswordRecoveryServiceTest` checks expiry, hashing, code replay, both rate
budgets, account-policy restrictions, changed credentials, and rollback rules.
`PasswordRecoveryControllerTest` checks signed-out access, generic responses,
validation, and CSRF protection. `PasswordRecoveryIntegrationTest` uses unique
synthetic accounts and a mocked in-memory sender to check MySQL upserts,
concurrent requests, persisted wrong attempts, and single-use password resets
across committed transactions. It cleans only its own synthetic records.
