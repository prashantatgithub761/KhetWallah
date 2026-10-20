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
