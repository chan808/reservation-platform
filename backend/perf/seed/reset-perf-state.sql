SET @perf_product_id = __PERF_PRODUCT_ID__;
SET @perf_stock = __PERF_PRODUCT_STOCK__;
SET @perf_now = NOW(6);

DELETE payment_webhooks
FROM payment_webhooks
INNER JOIN payments ON payments.id = payment_webhooks.payment_id
INNER JOIN orders ON orders.id = payments.order_id
WHERE orders.product_id = @perf_product_id;

DELETE order_status_histories
FROM order_status_histories
INNER JOIN orders ON orders.id = order_status_histories.order_id
WHERE orders.product_id = @perf_product_id;

DELETE payments
FROM payments
INNER JOIN orders ON orders.id = payments.order_id
WHERE orders.product_id = @perf_product_id;

DELETE FROM orders
WHERE product_id = @perf_product_id;

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
