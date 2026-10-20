-- KhetWallah additive upgrade: recovery, handover codes, notifications, reviews and crop details.
-- First take a backup. Select the existing application database before running this script.
-- This upgrades an existing KhetWallah schema; it does not initialize an empty database.
-- Re-running safely skips existing columns/tables. No data is deleted.

SET @kw_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='app_users' AND COLUMN_NAME='auth_version')=0,
 'ALTER TABLE app_users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE kw_statement FROM @kw_sql;
EXECUTE kw_statement;
DEALLOCATE PREPARE kw_statement;

SET @kw_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='marketplace_orders' AND COLUMN_NAME='pickup_revision')=0,
 'ALTER TABLE marketplace_orders ADD COLUMN pickup_revision BIGINT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE kw_statement FROM @kw_sql;
EXECUTE kw_statement;
DEALLOCATE PREPARE kw_statement;

SET @kw_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='variety')=0,
 'ALTER TABLE produce_listings ADD COLUMN variety VARCHAR(100) NULL', 'SELECT 1');
PREPARE kw_statement FROM @kw_sql;
EXECUTE kw_statement;
DEALLOCATE PREPARE kw_statement;

SET @kw_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='harvest_date')=0,
 'ALTER TABLE produce_listings ADD COLUMN harvest_date DATE NULL', 'SELECT 1');
PREPARE kw_statement FROM @kw_sql;
EXECUTE kw_statement;
DEALLOCATE PREPARE kw_statement;

SET @kw_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='minimum_order_quantity_kg')=0,
 'ALTER TABLE produce_listings ADD COLUMN minimum_order_quantity_kg DECIMAL(10,2) NOT NULL DEFAULT 0.01', 'SELECT 1');
PREPARE kw_statement FROM @kw_sql;
EXECUTE kw_statement;
DEALLOCATE PREPARE kw_statement;

SET @kw_sql = IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='packaging_details')=0,
 'ALTER TABLE produce_listings ADD COLUMN packaging_details VARCHAR(300) NULL', 'SELECT 1');
PREPARE kw_statement FROM @kw_sql;
EXECUTE kw_statement;
DEALLOCATE PREPARE kw_statement;

CREATE TABLE IF NOT EXISTS password_recoveries (
 email VARCHAR(254) NOT NULL PRIMARY KEY,
 code_hash VARCHAR(255) NOT NULL,
 expires_at DATETIME(6) NOT NULL,
 resend_at DATETIME(6) NOT NULL,
 window_started_at DATETIME(6) NOT NULL,
 send_count INT NOT NULL DEFAULT 0,
 failed_attempts INT NOT NULL DEFAULT 0,
 consumed_at DATETIME(6) NULL,
 bound_user_id BIGINT NULL,
 issued_auth_version BIGINT NULL
);
CREATE TABLE IF NOT EXISTS order_pickup_codes (
 order_id BIGINT NOT NULL PRIMARY KEY,
 code_hash VARCHAR(255) NULL,
 expires_at DATETIME(6) NULL,
 resend_at DATETIME(6) NOT NULL,
 window_started_at DATETIME(6) NOT NULL,
 send_count INT NOT NULL DEFAULT 0,
 failed_attempts INT NOT NULL DEFAULT 0,
 pickup_revision BIGINT NOT NULL DEFAULT 0,
 consumed_at DATETIME(6) NULL,
 CONSTRAINT fk_pickup_code_order FOREIGN KEY (order_id) REFERENCES marketplace_orders(id)
);
-- Back up the selected KhetWallah database before running this migration.
-- Run against your application database, not mysql/system schemas.
CREATE TABLE IF NOT EXISTS order_notifications (
 id BIGINT NOT NULL AUTO_INCREMENT,
 recipient_id BIGINT NOT NULL,
 order_id BIGINT NOT NULL,
 event_key VARCHAR(160) NOT NULL,
 event_type VARCHAR(24) NOT NULL,
 recipient_role VARCHAR(8) NOT NULL,
 crop_name VARCHAR(100) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 read_at DATETIME(6) NULL,
 PRIMARY KEY (id),
 CONSTRAINT uk_notification_event UNIQUE (event_key),
 INDEX idx_notification_recipient (recipient_id,id),
 CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES app_users(id),
 CONSTRAINT fk_notification_order FOREIGN KEY (order_id) REFERENCES marketplace_orders(id)
);
CREATE TABLE IF NOT EXISTS order_reviews (
 id BIGINT NOT NULL AUTO_INCREMENT,
 order_id BIGINT NOT NULL,
 listing_id BIGINT NOT NULL,
 buyer_id BIGINT NOT NULL,
 rating INT NOT NULL,
 comment VARCHAR(1000) NOT NULL,
 hidden BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY (id),
 CONSTRAINT uk_review_order UNIQUE (order_id),
 INDEX idx_review_listing (listing_id,hidden,id),
 CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
 CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES marketplace_orders(id),
 CONSTRAINT fk_review_listing FOREIGN KEY (listing_id) REFERENCES produce_listings(id),
 CONSTRAINT fk_review_buyer FOREIGN KEY (buyer_id) REFERENCES app_users(id)
);
