CREATE TABLE payment_outbox_messages
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    event_id       VARCHAR(64)   NOT NULL,
    topic          VARCHAR(120)  NOT NULL,
    message_key    VARCHAR(120)  NOT NULL,
    event_type     VARCHAR(60)   NOT NULL,
    payload        JSON          NOT NULL,
    published      TINYINT(1)    NOT NULL DEFAULT 0,
    attempt_count  INT           NOT NULL DEFAULT 0,
    last_error     VARCHAR(255)  NULL,
    published_at   DATETIME(6)   NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_outbox_messages_event_id UNIQUE (event_id),
    INDEX idx_payment_outbox_messages_publish (published, attempt_count, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE processed_kafka_messages
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    consumer_group VARCHAR(120)  NOT NULL,
    event_id       VARCHAR(64)   NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_processed_kafka_messages_group_event UNIQUE (consumer_group, event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
