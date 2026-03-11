CREATE TABLE products
(
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(150)  NOT NULL,
    description     TEXT          NULL,
    price           BIGINT        NOT NULL,
    stock_quantity  INT           NOT NULL,
    sale_start_at   DATETIME(6)   NOT NULL,
    sale_end_at     DATETIME(6)   NULL,
    status          VARCHAR(20)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT chk_products_price_non_negative CHECK (price >= 0),
    CONSTRAINT chk_products_stock_non_negative CHECK (stock_quantity >= 0),
    CONSTRAINT chk_products_sale_window CHECK (sale_end_at IS NULL OR sale_end_at > sale_start_at),
    INDEX idx_products_sale_start_at (sale_start_at),
    INDEX idx_products_status_sale_start_at (status, sale_start_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE orders
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    order_number         VARCHAR(40)   NOT NULL,
    member_id            BIGINT        NOT NULL,
    product_id           BIGINT        NOT NULL,
    order_request_id     VARCHAR(64)   NOT NULL,
    quantity             INT           NOT NULL,
    unit_price           BIGINT        NOT NULL,
    total_price          BIGINT        NOT NULL,
    status               VARCHAR(30)   NOT NULL,
    payment_type         VARCHAR(20)   NOT NULL,
    payment_deadline_at  DATETIME(6)   NULL,
    ordered_at           DATETIME(6)   NOT NULL,
    canceled_at          DATETIME(6)   NULL,
    cancel_reason        VARCHAR(255)  NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT uk_orders_request_id UNIQUE (order_request_id),
    CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_orders_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT chk_orders_total_price_non_negative CHECK (total_price >= 0),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES products (id),
    INDEX idx_orders_member_created_at (member_id, created_at),
    INDEX idx_orders_product_status (product_id, status),
    INDEX idx_orders_status_payment_deadline (status, payment_deadline_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE order_status_histories
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    order_id       BIGINT        NOT NULL,
    from_status    VARCHAR(30)   NULL,
    to_status      VARCHAR(30)   NOT NULL,
    reason         VARCHAR(255)  NULL,
    actor_type     VARCHAR(30)   NOT NULL,
    actor_id       VARCHAR(64)   NULL,
    created_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_status_histories_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_order_status_histories_order_created_at (order_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE payments
(
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL,
    payment_type      VARCHAR(20)   NOT NULL,
    payment_key       VARCHAR(200)  NULL,
    provider_order_id VARCHAR(100)  NULL,
    status            VARCHAR(30)   NOT NULL,
    amount            BIGINT        NOT NULL,
    approved_amount   BIGINT        NULL,
    canceled_amount   BIGINT        NULL,
    failure_code      VARCHAR(100)  NULL,
    failure_message   VARCHAR(255)  NULL,
    raw_response      JSON          NULL,
    requested_at      DATETIME(6)   NOT NULL,
    approved_at       DATETIME(6)   NULL,
    failed_at         DATETIME(6)   NULL,
    canceled_at       DATETIME(6)   NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_payment_key UNIQUE (payment_key),
    CONSTRAINT chk_payments_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_payments_status_requested_at (status, requested_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE payment_webhooks
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id         BIGINT        NULL,
    provider           VARCHAR(20)   NOT NULL,
    external_event_id  VARCHAR(100)  NULL,
    event_type         VARCHAR(100)  NOT NULL,
    payload            JSON          NOT NULL,
    processed          TINYINT(1)    NOT NULL DEFAULT 0,
    processed_at       DATETIME(6)   NULL,
    processing_error   VARCHAR(255)  NULL,
    created_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhooks_provider_event UNIQUE (provider, external_event_id),
    CONSTRAINT fk_payment_webhooks_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    INDEX idx_payment_webhooks_processed_created_at (processed, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
