# KhetWallah on Render and Aiven

Docker packages the Java application. Render runs that package online. Aiven
provides the separate MySQL database. No Docker Desktop installation or Docker
Hub account is needed when Render builds the Dockerfile directly from GitHub.

## 1. Create the free MySQL service first

1. Sign in at https://console.aiven.io/ and open your project.
2. Select **Services → Create service → MySQL**.
3. Select the **Free** service plan, not a paid plan or trial-only plan. Name it
   `khetwallah-db`. The Free plan assigns the cloud/region automatically.
4. Create the service and wait until its status is **Running**.
5. Open **Overview → Quick connect** or **Connection information**. Keep the
   hostname, port, database name, username and password private. Copy their real
   values from Aiven; words such as `YOUR_HOST` are not server addresses.
6. Download the project's CA certificate from that service's Overview page.

The service normally supplies a database such as `defaultdb`. Use the exact name
shown in Aiven. The database name does not need to be `KhetWallah`.

### Initialize the database before deploying

The application validates its schema and will NOT start with an empty database.
Expected tables are `app_users`, `produce_listings`, `marketplace_orders`,
`admin_actions`, `listing_reports`, `email_verifications`, and `email_outbox`.

For the real migration, export and back up the existing Railway database first,
then import its schema and data into Aiven. Apply only the missing upgrades from
`docs/`. Those SQL files are incremental migrations, not a complete initial
schema. Do not set `DDL_AUTO=create` or `create-drop` against existing data.

If starting a separate empty demo, import the complete current schema without
data instead. This makes a new empty site; it does not copy Railway accounts,
listings, orders or photos. Choose this deliberately rather than treating an
empty deployment as a successful migration.

Database export/import commands depend on the real connection details and MySQL
versions. Prepare those after the Aiven service exists. Do not paste passwords
into chat or put database dumps inside the Git repository.

## 2. Put the prepared application files on GitHub

The files added for Render are:

- `Dockerfile`: builds with Maven and Java 21, then runs a smaller Java 21 image
  under a non-root account.
- `.dockerignore`: excludes local tooling, private files and uploads from the
  Docker build context.
- `src/main/resources/application-render.properties`: HTTPS session settings
  and smaller connection/thread pools, active only in the `render` profile.

Review the working tree before committing. There are existing, uncommitted
features in this project; those need to be included intentionally if this deploy
should contain them. Do not commit `.idea`, passwords, API keys or SQL dumps.

Render builds the version on GitHub, not unsaved or unpushed files on your Mac.

## 3. Create the Render Web Service

Open https://dashboard.render.com/web/new and use **Git Provider** to connect
your GitHub repository. Configure:

| Field | Value |
| --- | --- |
| Repository | `prashantatgithub761/KhetWallah` |
| Name | `khetwallah` (or another available name) |
| Branch | `main` |
| Language / Runtime | **Docker** |
| Root directory | Leave blank; `pom.xml` is at the repository root |
| Dockerfile path | `./Dockerfile` |
| Docker command / start command override | Leave blank |
| Instance type | **Free** |
| Health check path | `/api/status` |

The application already reads Render's `PORT`; do not hard-code port 8080 in
Render. Docker's `EXPOSE 8080` is documentation, not a fixed runtime port.

## 4. Add private environment variables in Render

| Name | Value / source |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `render` |
| `DATABASE_URL` | Your actual JDBC MySQL connection URL; see TLS below |
| `DB_USERNAME` | Username from Aiven |
| `DB_PASSWORD` | Password from Aiven |
| `DDL_AUTO` | `validate` |
| `APP_PUBLIC_URL` | The exact assigned HTTPS Render origin, with no path or query |
| `EMAIL_ENABLED` | `false` during the initial hosting check; see email below |

The Dockerfile supplies `UPLOAD_DIR` and starting Java memory settings. It does
not include any database credentials. Optional `ADMIN_EMAIL` and `ADMIN_PASSWORD`
must be supplied together if you want admin bootstrap. Use an email not owned by
a regular account and a strong, unique password. For an existing admin, changing
the bootstrap password changes that admin's password at startup.

### Aiven TLS connection

The URL must begin with `jdbc:mysql://`, not `mysql://`, and must use Aiven's
external hostname/port, not `localhost` or `mysql.railway.internal`.

Connector/J supports `sslMode=VERIFY_IDENTITY` for encrypted connections with
certificate and hostname validation. Aiven's CA certificate may need a Java
truststore; do not disable certificate validation to resolve a connection error.
For a private CA, create a truststore outside the Git repository from the
downloaded CA, upload it as a Render secret file, and configure the matching
Connector/J truststore URL/type/password. Finish those values after downloading
the actual service CA. Keep credentials outside the URL where possible.

Never use the local `email-test` profile on Render. Its inbox runs on your Mac
at `127.0.0.1:8025`, which is not reachable from Render.

## 5. First deployment and check

After database initialization and configuration, create/deploy the service.
Watch for a successful build followed by `Started KhetWallahApplication`.

Open the exact assigned Render URL plus `/api/status`. It should return:

```json
{"application":"KhetWallah","message":"Connecting farmers and buyers"}
```

Open `/` next. After changing environment variables, allow Render to redeploy.
If boot reports a missing table/column, finish the schema migration rather than
restarting the unchanged application repeatedly.

## 6. Finish the public features

- **Email:** new registration requires OTP. With email disabled, old migrated
  accounts can sign in, but new registrations cannot receive codes. For real
  sending, configure `EMAIL_ENABLED=true`, `RESEND_API_KEY`, `EMAIL_FROM`, and the
  actual `APP_PUBLIC_URL`. A sender authorized by the provider is required.
- **Photos:** the current implementation stores images in `UPLOAD_DIR` on the
  server. Render Free has no persistent disk: uploaded images are lost on
  restart, redeploy or spin-down. Cloudinary/object-storage integration and photo
  migration are separate pending work; this Dockerfile does not implement them.
- **Memory:** the starting 256 MB Java heap is a demo configuration, not a load
  guarantee. Concurrent large photo uploads may exceed the free instance's RAM.
- **Sleep:** a free service sleeps after 15 minutes without traffic. First visits
  can be slow and queued order emails wait while the application is asleep.
- Keep Railway available until database records, photo links, login, checkout,
  order actions, admin access and real email have been verified on Render.

## Official references

- Render Docker deployment: https://render.com/docs/docker
- Render web services: https://render.com/docs/web-services
- Render free limitations: https://render.com/docs/free
- Aiven MySQL setup: https://aiven.io/docs/products/mysql/get-started
- Aiven free MySQL limits: https://aiven.io/docs/products/mysql/concepts/mysql-free-tier
- Aiven certificates: https://aiven.io/docs/platform/concepts/tls-ssl-certificates
- Connector/J server verification: https://dev.mysql.com/doc/connector-j/en/connector-j-server-authentication.html
