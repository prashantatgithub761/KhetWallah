# Marketplace tools and mobile experience

## Features and where to find them

- **Nearby discovery:** the marketplace supports district/PIN search and optional distance filtering. A farmer can add general pickup-area coordinates; these coordinates are public. Buyers explicitly choose Use my location. Their device position stays in the current tab's memory and is excluded from address-bar history and shared search links. No geocoding service, tracking history or map-provider account is required. Older listings need location details before they can match a distance filter.
- **Farmer profiles:** Meet the farmer links to `/farmer.html?id=USER_ID`. Profile editing is at `/farmer-profile.html`. Public details contain a display name, optional farm introduction, general district/PIN, current crops and real visible reviews from completed orders. They contain no account email or credentials. Ratings are not identity/organic certification.
- **Saved items:** `/saved.html` keeps crops and farmers private to the signed-in account. Save/remove is idempotent. Moderated or suspended targets disappear. Expired/closed/sold-out saved crops show their true availability and cannot receive new requests.
- **Availability:** farmers can set an available-until date, pause/resume, or mark sold out. Date expiry and availability changes affect new requests. Existing reserved orders remain visible and can be fulfilled or declined through their normal lifecycle. Resuming requires an unexpired date and sufficient stock. Admin moderation preserves the previous paused/sold-out state when restoring a hidden listing.
- **Order messages:** expand Order conversation on an order, or open `/messages.html`. Only buyer/farmer can access it. Unread state is per recipient. Message send budgets are enforced in MySQL across orders. Reported messages are visible to admins, who can hide/restore with an audit reason. Hidden message text is redacted from participant views and previews. No external chat/SMS provider or emails are used.
- **Seller insights:** `/insights.html` shows account-scoped order counts, completed quantity, completed-order snapshot value and top crops. KhetWallah does not track money received, so these figures are not payment or profit records.
- **Photo gallery:** a listing supports up to four pictures including its cover. JPEG/PNG inputs are checked, decoded and re-encoded as JPEG, scaled to 1600 pixels for smaller downloads, with 5 MB/8 MP input limits. Removing a cover promotes the oldest extra picture. File cleanup follows the database commit/rollback.
- **Mobile experience:** bottom Browse/Saved/Orders/Account navigation, a More menu, accessible gallery thumbnails, visible filter chips, retry feedback, and English/Hindi main-interface copy. Names, locations and authored messages/reviews stay unchanged. Some server error wording remains English.
- **Install/offline support:** a manifest and 192/512 icons support home-screen installation in compatible browsers. Installation UI varies by browser; iOS generally uses Add to Home Screen. Public interface assets can load from cache. Offline navigation shows a reconnect page. APIs, login/logout, private HTML and mutations are never cached or replayed. An update banner lets the user save work before reloading.

## Apply this upgrade

1. Back up the application database and photos outside Git. For the local installation, `python3 docs/backup-local.py --output /absolute/path/outside/project` uses the existing private IntelliJ/environment settings. It never connects to a remote database. Stop writes if an exact matching database/photo snapshot is required.
2. Select the same existing application database used by Spring. Apply `docs/marketplace-tools-migration.sql` using the MySQL client. It adds columns/tables/indexes and is rerunnable; it does not initialize a blank database or remove records. Previous feature migrations must already be applied.
3. Keep `DDL_AUTO=validate`, run the checks and restart the application. A hosted database must be backed up and migrated separately before deploying this code.
4. Add general location information to listings that should appear in district/PIN/distance searches. No values are guessed for older records.

## Photos, email and hosting readiness

The Docker image already uses `/app/uploads` through `UPLOAD_DIR`. Mount persistent storage at the same location and confirm the service can read/write there. Setting a path does not make ephemeral storage durable. Back up the database and uploads together, protect backups and test restoration into separate copies. The local helper archives both. Use the host's database and volume backup/export tools for hosted data; this update does not provision a hosting account or schedule remote backups.

The existing email adapter and `docs/EMAIL-SETUP.md` explain real delivery using private sender settings. Signup/recovery have been tested with the local-only inbox. A verified sender account/API key is still required for real email; never deploy the `email-test` profile to a public host. No credentials should be committed to Git.

The included GitHub workflow runs Java unit/controller checks and `node docs/check-pwa.mjs`. Local MySQL integration tests additionally check distance queries, messages, saved items, insights and file rollback. Use a dedicated database for any CI database tests. The old `OrderPersistenceIntegrationTest` profile creates/drops its test schema and must never be run against the live application database.

Public UI pages and APIs still pass through Spring Security; local preview assets are served by the actual Java app. Before publishing: migrate the backed-up hosted schema, verify persistent uploads and backups, connect the email sender, pass CI, then deploy and smoke-test with disposable accounts.

## References

- [MDN: installable progressive web apps](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Making_PWAs_installable)
- [MDN: service workers](https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API/Using_Service_Workers)
- [MDN: optional device location](https://developer.mozilla.org/en-US/docs/Web/API/Geolocation/getCurrentPosition)
