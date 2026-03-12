SET @payment_outbox_updated_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payment_outbox_messages'
      AND column_name = 'updated_at'
);

SET @payment_outbox_updated_at_sql = IF(
    @payment_outbox_updated_at_exists = 0,
    'ALTER TABLE payment_outbox_messages ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
    'SELECT 1'
);

PREPARE payment_outbox_stmt FROM @payment_outbox_updated_at_sql;
EXECUTE payment_outbox_stmt;
DEALLOCATE PREPARE payment_outbox_stmt;

SET @processed_kafka_updated_at_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'processed_kafka_messages'
      AND column_name = 'updated_at'
);

SET @processed_kafka_updated_at_sql = IF(
    @processed_kafka_updated_at_exists = 0,
    'ALTER TABLE processed_kafka_messages ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)',
    'SELECT 1'
);

PREPARE processed_kafka_stmt FROM @processed_kafka_updated_at_sql;
EXECUTE processed_kafka_stmt;
DEALLOCATE PREPARE processed_kafka_stmt;
