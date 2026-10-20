-- Run once against the existing KhetWallah MySQL database before starting the new code
-- when DDL_AUTO=validate. Back up the database first.
ALTER TABLE app_users
    ADD COLUMN role varchar(16) NOT NULL DEFAULT 'USER',
    ADD COLUMN suspended bit(1) NOT NULL DEFAULT b'0';

CREATE TABLE admin_actions (
    id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_email varchar(254) NOT NULL,
    action varchar(30) NOT NULL,
    target_type varchar(30) NOT NULL,
    target_id bigint NOT NULL,
    reason varchar(500) NOT NULL,
    created_at datetime(6) NOT NULL
);
