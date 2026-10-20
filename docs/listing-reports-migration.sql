-- Run once on the intended KhetWallah database before deploying reports.
-- Requires the existing app_users and produce_listings tables. Back up first.
CREATE TABLE listing_reports (
    id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    listing_id bigint NOT NULL,
    reporter_id bigint NOT NULL,
    reason varchar(500) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'OPEN',
    created_at datetime(6) NOT NULL,
    resolved_at datetime(6),
    resolved_by varchar(254),
    resolution_reason varchar(500),
    CONSTRAINT uk_report_listing_user UNIQUE (listing_id, reporter_id),
    CONSTRAINT fk_report_listing FOREIGN KEY (listing_id) REFERENCES produce_listings(id),
    CONSTRAINT fk_report_user FOREIGN KEY (reporter_id) REFERENCES app_users(id),
    INDEX ix_reports_queue (status, created_at, id)
);
