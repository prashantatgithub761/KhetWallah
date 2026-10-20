-- Marketplace discovery, farmer profiles, saved items, messages and photo galleries.
-- Back up the selected application database before applying. This upgrades the existing schema.
-- No tables or records are dropped. Re-running skips existing columns/tables/indexes.

SET @kw_sql=IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='district')=0,'ALTER TABLE produce_listings ADD COLUMN district VARCHAR(100) NULL','SELECT 1');
PREPARE kw_stmt FROM @kw_sql; EXECUTE kw_stmt; DEALLOCATE PREPARE kw_stmt;

SET @kw_sql=IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='pin_code')=0,'ALTER TABLE produce_listings ADD COLUMN pin_code VARCHAR(6) NULL','SELECT 1');
PREPARE kw_stmt FROM @kw_sql; EXECUTE kw_stmt; DEALLOCATE PREPARE kw_stmt;

SET @kw_sql=IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='latitude')=0,'ALTER TABLE produce_listings ADD COLUMN latitude DECIMAL(9,6) NULL','SELECT 1');
PREPARE kw_stmt FROM @kw_sql; EXECUTE kw_stmt; DEALLOCATE PREPARE kw_stmt;

SET @kw_sql=IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='longitude')=0,'ALTER TABLE produce_listings ADD COLUMN longitude DECIMAL(9,6) NULL','SELECT 1');
PREPARE kw_stmt FROM @kw_sql; EXECUTE kw_stmt; DEALLOCATE PREPARE kw_stmt;

SET @kw_sql=IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='available_until')=0,'ALTER TABLE produce_listings ADD COLUMN available_until DATE NULL','SELECT 1');
PREPARE kw_stmt FROM @kw_sql; EXECUTE kw_stmt; DEALLOCATE PREPARE kw_stmt;

CREATE TABLE IF NOT EXISTS listing_photos (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 listing_id BIGINT NOT NULL,
 file_name VARCHAR(60) NOT NULL,
 KEY idx_listing_photos_listing (listing_id,id),
 CONSTRAINT fk_listing_photos_listing FOREIGN KEY (listing_id) REFERENCES produce_listings(id)
) ENGINE=InnoDB;

-- Additive: keep all existing marketplace data. Back up before applying.
CREATE TABLE IF NOT EXISTS farmer_profiles (
 user_id BIGINT NOT NULL PRIMARY KEY,
 farm_name VARCHAR(100) NULL,
 bio VARCHAR(1000) NULL,
 district VARCHAR(100) NULL,
 pin_code VARCHAR(6) NULL,
 CONSTRAINT fk_farmer_profile_user FOREIGN KEY (user_id) REFERENCES app_users(id)
);
CREATE TABLE IF NOT EXISTS saved_items (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_id BIGINT NOT NULL,
 target_type VARCHAR(10) NOT NULL,
 target_id BIGINT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_saved_account_target UNIQUE(account_id,target_type,target_id),
 INDEX idx_saved_account(account_id,target_type,id),
 CONSTRAINT fk_saved_item_account FOREIGN KEY (account_id) REFERENCES app_users(id)
);

-- Additive messaging schema. Back up the selected KhetWallah schema before applying.
CREATE TABLE IF NOT EXISTS order_messages (
 id BIGINT NOT NULL AUTO_INCREMENT,
 order_id BIGINT NOT NULL,
 sender_id BIGINT NOT NULL,
 recipient_id BIGINT NOT NULL,
 body VARCHAR(2000) NOT NULL,
 hidden BOOLEAN NOT NULL DEFAULT FALSE,
 created_at DATETIME(6) NOT NULL,
 read_at DATETIME(6) NULL,
 PRIMARY KEY(id),
 KEY ix_message_order_id(order_id,id),
 KEY ix_message_recipient_read(recipient_id,read_at),
 KEY ix_message_sender_time(sender_id,created_at),
 CONSTRAINT fk_message_order FOREIGN KEY(order_id) REFERENCES marketplace_orders(id),
 CONSTRAINT fk_message_sender FOREIGN KEY(sender_id) REFERENCES app_users(id),
 CONSTRAINT fk_message_recipient FOREIGN KEY(recipient_id) REFERENCES app_users(id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS message_reports (
 id BIGINT NOT NULL AUTO_INCREMENT,
 message_id BIGINT NOT NULL,
 reporter_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 PRIMARY KEY(id),
 UNIQUE KEY uk_message_reporter(message_id,reporter_id),
 KEY ix_message_reporter_time(reporter_id,created_at),
 CONSTRAINT fk_message_report_message FOREIGN KEY(message_id) REFERENCES order_messages(id),
 CONSTRAINT fk_message_report_reporter FOREIGN KEY(reporter_id) REFERENCES app_users(id)
) ENGINE=InnoDB;

SET @kw_sql=IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND INDEX_NAME='idx_listing_pin_status')=0,'CREATE INDEX idx_listing_pin_status ON produce_listings(pin_code,status)','SELECT 1');
PREPARE kw_stmt FROM @kw_sql; EXECUTE kw_stmt; DEALLOCATE PREPARE kw_stmt;

SET @kw_sql=IF((SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='produce_listings' AND COLUMN_NAME='status_before_hide')=0,'ALTER TABLE produce_listings ADD COLUMN status_before_hide VARCHAR(16) NULL','SELECT 1');
PREPARE kw_stmt FROM @kw_sql; EXECUTE kw_stmt; DEALLOCATE PREPARE kw_stmt;
