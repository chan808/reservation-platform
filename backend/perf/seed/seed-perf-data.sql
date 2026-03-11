SET @perf_product_name = '__PERF_PRODUCT_NAME__';
SET @perf_product_description = 'Performance load test product';
SET @perf_product_price = 10000;
SET @perf_product_stock = __PERF_PRODUCT_STOCK__;
SET @perf_now = NOW(6);

INSERT IGNORE INTO members (
    email,
    password,
    role,
    email_verified,
    provider,
    provider_id,
    nickname,
    created_at,
    updated_at
)
VALUES
__PERF_MEMBER_VALUES__;

UPDATE products
SET
    description = @perf_product_description,
    price = @perf_product_price,
    stock_quantity = @perf_product_stock,
    sale_start_at = @perf_now,
    sale_end_at = NULL,
    status = 'ON_SALE',
    updated_at = @perf_now
WHERE id = (
    SELECT product_id
    FROM (
        SELECT id AS product_id
        FROM products
        WHERE name = @perf_product_name
        ORDER BY id ASC
        LIMIT 1
    ) existing_product
);

INSERT INTO products (
    name,
    description,
    price,
    stock_quantity,
    sale_start_at,
    sale_end_at,
    status,
    version,
    created_at,
    updated_at
)
SELECT
    @perf_product_name,
    @perf_product_description,
    @perf_product_price,
    @perf_product_stock,
    @perf_now,
    NULL,
    'ON_SALE',
    0,
    @perf_now,
    @perf_now
WHERE NOT EXISTS (
    SELECT 1
    FROM products
    WHERE name = @perf_product_name
);

SELECT id, name, stock_quantity, price, sale_start_at, status
FROM products
WHERE name = @perf_product_name
ORDER BY id ASC
LIMIT 1;
