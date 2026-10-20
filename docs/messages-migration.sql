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
