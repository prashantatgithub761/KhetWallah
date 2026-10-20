# Marketplace features upgrade

## What changed

- Password recovery for ordinary accounts: `/recovery.html`, using the registered email, a six-digit 10-minute single-use code, and a new 15–128 character password. Old sessions are rejected on their next request. Recovery responses do not disclose whether an address has an account. Administrators rotate the private `ADMIN_PASSWORD` setting instead; see [recovery details](PASSWORD-RECOVERY.md).
- Private updates: `/notifications.html` lists requests, farmer decisions, cancellations, changed pickup arrangements and collection confirmations. Updates are recorded with the order transaction; no historical updates are invented. Each participant can mark only their own notifications as read.
- Confirmed collection: an accepted order with pickup details lets the buyer generate a six-digit 15-minute pickup code. At handover, the buyer shares it after receiving produce. The farmer enters it to complete collection. The server stores a hash, limits guesses/resends and invalidates a code when pickup arrangements change. Old `/complete` calls return 409 so they cannot bypass confirmation. The code is an order handover confirmation, not proof of payment or independent crop quality.
- Reviews: the buyer can submit one 1–5 star review after completion. The crop page shows visible reviews and their average. Administrators can hide/restore a review with an audit reason. Hidden reviews do not contribute to public counts/averages.
- Crop details: variety, a farmer-reported harvest date, minimum order quantity and packaging details. Existing listings default to a 0.01 kg minimum. Buyers cannot request below the minimum; stock below that minimum stays visible but cannot be ordered until the farmer updates it. Harvest dates are not independently verified.
- English/Hindi: a persistent header switch translates the main navigation, marketplace, account and order flows. Crop names, people, addresses and reviews remain in the language their author used. Some technical/server error text and long administrative explanations can remain English.

## Apply to an existing database before starting the new build

1. Back up the database and uploaded-photo directory. Keep backups outside the Git repository and restrict access.
2. Connect to the actual application database. In the `mysql>` prompt check `SELECT DATABASE();` and `SHOW TABLES;`. The database must already have `app_users`, `produce_listings`, and `marketplace_orders` plus previous upgrades.
3. From the repository root in Terminal, use your private MySQL connection configuration:

   `mysql --defaults-extra-file=/absolute/path/to/private-client.cnf your_database < docs/real-world-features-migration.sql`

   Or inside `mysql>` after selecting the database, use `SOURCE /absolute/path/to/KhetWallah/docs/real-world-features-migration.sql;`. `SOURCE` is a MySQL client command; do not paste it as SQL passed to `mysql -e`.
4. Keep `DDL_AUTO=validate`. The upgrade is additive and rerunnable. If it fails, fix the reported mismatch rather than using `create` on existing data.
5. Run the checks and restart the application. Local database changes do not migrate the hosted database; repeat the backed-up migration there before deploying the new code.

## Local email testing and real email

Existing `docs/run-email-test.py` runs the local-only inbox. It supports signup and recovery without sending real emails. Keep this inbox bound to localhost. Its contents include temporary codes, so do not publish it.

For real delivery, connect a verified sender using the existing Resend adapter and private `EMAIL_ENABLED`, `RESEND_API_KEY`, `EMAIL_FROM` and `APP_PUBLIC_URL` settings in the host. See the existing email setup document. No provider account/domain was configured for this upgrade. Never deploy the `email-test` profile publicly. Order email uses the durable outbox; in-app notifications work independently of email availability.

## Before publishing

- Use a persistent volume/object store for photos and back up both the volume and database. `UPLOAD_DIR` only selects a location; it does not make an ephemeral disk durable.
- Keep a restore-tested backup before each schema upgrade. `mysqldump --single-transaction` is suitable for the application's InnoDB data; restore into a separate database first. Back up uploads at the same time.
- The included `.github/workflows/verify.yml` runs unit/controller checks and packages Java 21 on pushes/pull requests. It does not deploy or modify hosted data. Enable the host's wait-for-CI setting to prevent deployment on a failed build. MySQL integration tests require a dedicated test database and are not run by that workflow.
- Action configuration follows the official [checkout](https://github.com/actions/checkout) and [setup-java](https://github.com/actions/setup-java) documentation.

## Check the feature flow

Create two disposable test accounts. Farmer posts a crop with a 5 kg minimum, buyer requests at least 5 kg, farmer accepts and sets pickup details, buyer generates a code, farmer confirms it, buyer reviews the completed order, then administrator hides/restores that review. Check each participant's notification page. Switch English/Hindi while entering pickup/review text to confirm it is preserved. A different account must not read the order or notification.
