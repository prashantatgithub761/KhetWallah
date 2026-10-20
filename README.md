# 🌾 KhetWallah

KhetWallah is a farmer-to-buyer marketplace built with Java, Spring Boot, Spring Security, JPA, and MySQL.

Farmers can publish produce listings, manage their stock, and handle orders. Buyers can search available produce, place orders, cancel pending orders, and follow their order status.

## Features

- Email OTP verification before account creation, with password hashing
- Queued order emails with delivery retries and a local test inbox
- Session-based login and logout
- CSRF protection
- Public listing search and pagination
- Owner-linked crop listings
- Owner-only listing editing and closing
- Buyer order placement
- Transactional stock reservation
- Buyer cancellation with stock restoration
- Farmer acceptance and rejection
- Pickup completion workflow
- Separate buyer and farmer order views
- Admin moderation of listings and accounts, with reasons recorded in an audit log
- Unit, controller, service, and MySQL integration tests

## Order lifecycle

```text
PENDING
├── ACCEPTED → COMPLETED
├── REJECTED
└── CANCELLED

```

## Run locally

Requirements: Java 21, MySQL, and Maven (the project includes `mvnw`).

1. Create a MySQL database named `KhetWallah`.
2. Set `DB_USERNAME` and `DB_PASSWORD` to your MySQL credentials.
3. Start the application with `./mvnw spring-boot:run`.
4. Open http://localhost:8080/.

The application uses `ddl-auto=validate`, so its database tables must already exist.

## Admin moderation

1. Back up the existing MySQL database. Run `docs/admin-migration.sql` **once** against the `KhetWallah` database **before** deploying this code, including on Railway. Existing accounts remain `USER`. For this schema change, use the reviewed SQL even if the host currently has `DDL_AUTO=update`; adding required columns to a populated table can otherwise fail.
2. Set private environment variables `ADMIN_EMAIL` (an unused email address) and `ADMIN_PASSWORD` (at least 15 characters). Do not put the password in code, Git, or a public screenshot. Set them on the KhetWallah app service, not the MySQL service. If either value is missing while the other is present, startup stops with a clear error.
3. Restart the app. Open `/login` and sign in with those credentials, then open `/admin.html`. An **Admin** link also appears on the homepage for an admin session. Changing `ADMIN_PASSWORD` in the environment and restarting rotates the admin password.
4. Admins can hide and restore listings, suspend and reinstate regular accounts, review accounts and listings, and see the last 20 moderation actions. Every action requires a reason. Suspending an account hides all its active listings; reinstating the account does **not** automatically republish those listings. Review and restore them individually.

Only admin sessions can access `/admin.html` or `/api/admin/**`. Suspended users cannot sign in, and existing authenticated sessions are invalidated on their next request. Hiding a listing removes it from public search and blocks new orders and photo access. Existing orders remain in their current state; a moderator should review them separately with the affected people.

## Test

The integration tests use a separate database named `khetwallah_integration_test` and a MySQL user named `khetwallah_test`. Set `TEST_DB_PASSWORD`, then run `./mvnw test`. Never point the integration tests at your main database.

## Report a listing

Before starting this version with `DDL_AUTO=validate`, run `docs/listing-reports-migration.sql` once against the intended database. Apply it separately to local MySQL and Railway before deploying there. This is additional to the admin migration above.

Every marketplace card includes **Report this listing**. A signed-in, active user can submit a reason (1–500 characters). Users cannot report their own listing. Each account may report a listing once, including after resolution; other users may independently report the same listing. Reporting does not automatically hide content.

The admin page includes **Reported listings**, sorted oldest first and paginated, with open, dismissed, and hidden-resolution filters. An admin can dismiss with an explanation or hide the listing and resolve the report in one transaction. Already hidden listings can have their reports resolved without hiding again. For closed listings, dismiss the report with an explanation. Other reports on the same listing remain individually reviewable. Reporter identity and report text are only returned through admin APIs. All decisions are recorded in moderation history. Reporting does not cancel existing orders.

Manual check: sign in as a buyer, report another user's active listing, and verify a second submission is rejected. As admin, open the report queue, hide and resolve it, then verify it disappears from public search. Check the resolved filter and audit history. Repeat with dismiss and verify the listing stays visible. A regular user must receive 403 from the admin report API.


## Signup verification and order emails

Before restarting this version, add `docs/email-verification-migration.sql` and
`docs/email-outbox-migration.sql` to the intended database. The complete
[local testing and production setup guide](docs/EMAIL-SETUP.md) explains the new
signup flow, limits, the `email-test` profile, and the local inbox at port 8025.
Normal delivery defaults to disabled until a provider is configured. Existing
accounts keep working; new accounts require OTP verification.


## Marketplace features upgrade

Password recovery, private notifications, buyer pickup codes, completed-order reviews, crop metadata, and English/Hindi controls are available locally. See [feature guide and database migration](docs/REAL-WORLD-FEATURES.md) before deploying this upgrade. Real email requires a verified sender.


## Discovery and mobile marketplace tools

Farmer profiles, saved crops/farmers, district/PIN and nearby discovery, listing availability, private order conversations, seller insights, photo galleries and install/offline support are implemented. See [setup, schema and hosting guide](docs/MARKETPLACE-TOOLS.md).
