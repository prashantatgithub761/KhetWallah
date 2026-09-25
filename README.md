# 🌾 KhetWallah

KhetWallah is a farmer-to-buyer marketplace built with Java, Spring Boot, Spring Security, JPA, and MySQL.

Farmers can publish produce listings, manage their stock, and handle orders. Buyers can search available produce, place orders, cancel pending orders, and follow their order status.

## Features

- Account registration with password hashing
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

## Test

The integration tests use a separate database named `khetwallah_integration_test` and a MySQL user named `khetwallah_test`. Set `TEST_DB_PASSWORD`, then run `./mvnw test`. Never point the integration tests at your main database.