-- Apply once after taking a backup. Root release migration adds column guards for re-runs.
ALTER TABLE produce_listings
  ADD COLUMN district VARCHAR(100) NULL,
  ADD COLUMN pin_code VARCHAR(6) NULL,
  ADD COLUMN latitude DECIMAL(9,6) NULL,
  ADD COLUMN longitude DECIMAL(9,6) NULL,
  ADD COLUMN available_until DATE NULL;
CREATE TABLE IF NOT EXISTS listing_photos (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 listing_id BIGINT NOT NULL,
 file_name VARCHAR(60) NOT NULL,
 KEY idx_listing_photos_listing (listing_id,id),
 CONSTRAINT fk_listing_photos_listing FOREIGN KEY (listing_id) REFERENCES produce_listings(id)
) ENGINE=InnoDB;
