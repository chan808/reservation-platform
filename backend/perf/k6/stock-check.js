import http from 'k6/http';
import { fail } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const productId = Number(__ENV.PRODUCT_ID || '0');
const initialStock = Number(__ENV.INITIAL_STOCK || '0');
const orderQuantity = Number(__ENV.ORDER_QUANTITY || '1');
const summaryFile = __ENV.SUMMARY_FILE || '';
const summary = summaryFile ? JSON.parse(open(summaryFile)) : null;

export const options = {
  vus: 1,
  iterations: 1,
};

export default function () {
  if (!productId) {
    fail('PRODUCT_ID is required.');
  }

  if (!summaryFile) {
    fail('SUMMARY_FILE is required.');
  }

  if (initialStock < 0) {
    fail('INITIAL_STOCK must be zero or greater.');
  }

  if (!Number.isInteger(orderQuantity) || orderQuantity <= 0) {
    fail(`ORDER_QUANTITY must be a positive integer. Received: ${orderQuantity}`);
  }

  const confirmedCount = Number(
    summary &&
      summary.metrics &&
      summary.metrics.payments_confirmed &&
      summary.metrics.payments_confirmed.count
      ? summary.metrics.payments_confirmed.count
      : 0,
  );
  const expectedRemainingStock = initialStock - confirmedCount * orderQuantity;

  const productResponse = http.get(`${baseUrl}/api/products/${productId}`);
  if (productResponse.status !== 200) {
    fail(`Failed to fetch product ${productId}. status=${productResponse.status}`);
  }

  const productBody = productResponse.json();
  const product = productBody && productBody.data;
  if (!product) {
    fail('Product response has no data field.');
  }

  const actualRemainingStock = Number(product.stockQuantity);
  const consistent = actualRemainingStock === expectedRemainingStock;

  console.log(
    JSON.stringify(
      {
        type: 'stock-check',
        productId,
        initialStock,
        confirmedCount,
        orderQuantity,
        expectedRemainingStock,
        actualRemainingStock,
        consistent,
      },
      null,
      2,
    ),
  );

  if (!consistent) {
    fail(
      `Stock mismatch detected. expectedRemainingStock=${expectedRemainingStock}, actualRemainingStock=${actualRemainingStock}`,
    );
  }
}
