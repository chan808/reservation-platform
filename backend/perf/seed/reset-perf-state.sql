SET @perf_product_id = __PERF_PRODUCT_ID__;
SET @perf_stock = __PERF_PRODUCT_STOCK__;
SET @perf_now = NOW(6);

CREATE TEMPORARY TABLE perf_order_ids
(
    id BIGINT PRIMARY KEY
);

INSERT INTO perf_order_ids (id)
SELECT id
FROM orders
WHERE product_id = @perf_product_id;

DELETE FROM payment_outbox_messages
WHERE message_key IN (
    SELECT CAST(id AS CHAR(20))
    FROM perf_order_ids
);

DELETE payment_webhooks
FROM payment_webhooks
INNER JOIN payments ON payments.id = payment_webhooks.payment_id
WHERE payments.order_id IN (
    SELECT id
    FROM perf_order_ids
);

DELETE order_status_histories
FROM order_status_histories
WHERE order_status_histories.order_id IN (
    SELECT id
    FROM perf_order_ids
);

DELETE payments
FROM payments
WHERE payments.order_id IN (
    SELECT id
    FROM perf_order_ids
);

DELETE order_status_histories
FROM order_status_histories
WHERE order_status_histories.order_id IN (
    SELECT id
    FROM perf_order_ids
);

DELETE FROM orders
WHERE id IN (
    SELECT id
    FROM perf_order_ids
);

DROP TEMPORARY TABLE perf_order_ids;

UPDATE products
SET
    stock_quantity = @perf_stock,
    sale_start_at = @perf_now,
    sale_end_at = NULL,
    status = 'ON_SALE',
    updated_at = @perf_now
WHERE id = @perf_product_id;

SELECT id, name, stock_quantity, price, status
FROM products
WHERE id = @perf_product_id;
