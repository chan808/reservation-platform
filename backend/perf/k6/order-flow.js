import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const baseUrl = (__ENV.BASE_URL || 'http://host.docker.internal:8080').replace(/\/$/, '');
const productId = Number(__ENV.PRODUCT_ID || '0');
const paymentType = (__ENV.PAYMENT_TYPE || 'PAYPAL').toUpperCase();
const orderQuantity = Number(__ENV.ORDER_QUANTITY || '1');
const targetVus = Number(__ENV.TARGET_VUS || '100');
const duration = __ENV.DURATION || '60s';
const scenarioMode = (__ENV.SCENARIO_MODE || 'constant').toLowerCase();
const burstRampUp = __ENV.BURST_RAMP_UP || '5s';
const burstHold = __ENV.BURST_HOLD || '30s';
const burstRampDown = __ENV.BURST_RAMP_DOWN || '5s';
const thinkTimeMs = Number(__ENV.THINK_TIME_MS || '0');
const usersCsvPath = __ENV.USERS_CSV || '';
const tokensCsvPath = __ENV.TOKENS_CSV || '';
const requireTokens = (__ENV.REQUIRE_TOKENS || '').toLowerCase() === 'true';
const summaryFile = __ENV.SUMMARY_FILE || `/scripts/results/order-flow-${targetVus}vu.json`;
const requestTimeout = __ENV.REQUEST_TIMEOUT || '30s';

const completedFlows = new Counter('completed_flows');
const paymentsConfirmed = new Counter('payments_confirmed');
const businessErrors = new Counter('business_errors');
const soldOutErrors = new Counter('sold_out_errors');
const authFailures = new Counter('auth_failures');
const productLookupFailures = new Counter('product_lookup_failures');
const orderCreateFailures = new Counter('order_create_failures');
const paymentConfirmFailures = new Counter('payment_confirm_failures');
const orderVerifyFailures = new Counter('order_verify_failures');
const stockNegativeChecks = new Rate('stock_negative_rate');
const orderFlowSuccessRate = new Rate('order_flow_success_rate');
const loginDuration = new Trend('login_duration', true);
const productLookupDuration = new Trend('product_lookup_duration', true);
const orderCreateDuration = new Trend('order_create_duration', true);
const paymentConfirmDuration = new Trend('payment_confirm_duration', true);
const orderVerifyDuration = new Trend('order_verify_duration', true);
const orderFlowDuration = new Trend('order_flow_duration', true);

const users = new SharedArray('load-test-users', () => parseCsv(usersCsvPath, ['email', 'password']));
const tokens = new SharedArray('load-test-tokens', () => parseCsv(tokensCsvPath, ['email', 'member_id', 'access_token']));

const vuSession = {
  accessToken: null,
  email: null,
};

export const options = buildOptions();

export function setup() {
  const identities = getIdentitySource();

  if (!productId) {
    fail('PRODUCT_ID is required.');
  }

  if (!Number.isInteger(orderQuantity) || orderQuantity <= 0) {
    fail(`ORDER_QUANTITY must be a positive integer. Received: ${orderQuantity}`);
  }

  if (identities.length < targetVus) {
    fail(`Identity rows must be at least ${targetVus}. Current count: ${identities.length}`);
  }

  const productResponse = http.get(`${baseUrl}/api/products/${productId}`, {
    tags: { name: 'product_get_setup' },
    timeout: requestTimeout,
  });

  if (productResponse.status !== 200) {
    fail(`Failed to fetch product ${productId}. status=${productResponse.status}`);
  }

  const productBody = parseJson(productResponse, 'setup product response');
  const product = productBody && productBody.data;
  if (!product) {
    fail('Product response has no data field.');
  }

  if (product.status !== 'ON_SALE') {
    fail(`Product ${productId} must be ON_SALE. Current status=${product.status}`);
  }

  console.log(
    JSON.stringify({
      type: 'setup',
      productId: productId,
      initialStock: product.stockQuantity,
      paymentType: paymentType,
      targetVus: targetVus,
      duration: duration,
      scenarioMode: scenarioMode,
      burstRampUp: burstRampUp,
      burstHold: burstHold,
      burstRampDown: burstRampDown,
      identityCount: identities.length,
      usePreGeneratedTokens: tokens.length > 0,
    }),
  );

  return {
    initialStock: product.stockQuantity,
  };
}

export default function () {
  const flowStartedAt = Date.now();
  const identity = getIdentityForCurrentVu();
  ensureAuth(identity);

  const authHeaders = {
    Authorization: `Bearer ${vuSession.accessToken}`,
    'Content-Type': 'application/json',
  };

  const productGet = http.get(`${baseUrl}/api/products/${productId}`, {
    headers: authHeaders,
    tags: { name: 'product_get' },
    timeout: requestTimeout,
  });
  productLookupDuration.add(productGet.timings.duration);

  if (!check(productGet, { 'product lookup status is 200': (response) => response.status === 200 })) {
    productLookupFailures.add(1);
    recordFailure(productGet);
    orderFlowSuccessRate.add(false);
    return;
  }

  const productBody = parseJson(productGet, 'product lookup');
  const product = productBody && productBody.data;
  if (!product || product.status !== 'ON_SALE') {
    productLookupFailures.add(1);
    businessErrors.add(1);
    orderFlowSuccessRate.add(false);
    return;
  }

  stockNegativeChecks.add(product.stockQuantity < 0);

  const createOrderPayload = JSON.stringify({
    productId: productId,
    quantity: orderQuantity,
    orderRequestId: buildOrderRequestId(),
    paymentType: paymentType,
  });

  const orderCreate = http.post(`${baseUrl}/api/orders`, createOrderPayload, {
    headers: authHeaders,
    tags: { name: 'order_create' },
    timeout: requestTimeout,
  });
  orderCreateDuration.add(orderCreate.timings.duration);

  if (!check(orderCreate, { 'order create status is 201': (response) => response.status === 201 })) {
    orderCreateFailures.add(1);
    recordFailure(orderCreate);
    orderFlowSuccessRate.add(false);
    return;
  }

  const createdOrderBody = parseJson(orderCreate, 'order create');
  const createdOrder = createdOrderBody && createdOrderBody.data;
  if (!createdOrder || !createdOrder.id || !createdOrder.paymentId || !createdOrder.totalPrice) {
    orderCreateFailures.add(1);
    businessErrors.add(1);
    orderFlowSuccessRate.add(false);
    return;
  }

  const confirmPayload = JSON.stringify({
    paymentKey: createdOrder.paymentId,
    amount: createdOrder.totalPrice,
  });

  const paymentConfirm = http.post(
    `${baseUrl}/api/orders/${createdOrder.id}/confirm-payment`,
    confirmPayload,
    {
      headers: authHeaders,
      tags: { name: 'payment_confirm' },
      timeout: requestTimeout,
    },
  );
  paymentConfirmDuration.add(paymentConfirm.timings.duration);

  if (!check(paymentConfirm, { 'payment confirm status is 200': (response) => response.status === 200 })) {
    paymentConfirmFailures.add(1);
    recordFailure(paymentConfirm);
    orderFlowSuccessRate.add(false);
    return;
  }

  const confirmBody = parseJson(paymentConfirm, 'payment confirm');
  const paymentResult = confirmBody && confirmBody.data;
  if (!paymentResult || paymentResult.status !== 'SUCCEEDED') {
    paymentConfirmFailures.add(1);
    businessErrors.add(1);
    orderFlowSuccessRate.add(false);
    return;
  }

  const orderVerify = http.get(`${baseUrl}/api/orders/${createdOrder.id}`, {
    headers: authHeaders,
    tags: { name: 'order_get' },
    timeout: requestTimeout,
  });
  orderVerifyDuration.add(orderVerify.timings.duration);

  if (!check(orderVerify, { 'order verify status is 200': (response) => response.status === 200 })) {
    orderVerifyFailures.add(1);
    recordFailure(orderVerify);
    orderFlowSuccessRate.add(false);
    return;
  }

  const verifiedOrderBody = parseJson(orderVerify, 'order verify');
  const verifiedOrder = verifiedOrderBody && verifiedOrderBody.data;
  if (!verifiedOrder || verifiedOrder.status !== 'PAID' || verifiedOrder.paymentStatus !== 'SUCCEEDED') {
    orderVerifyFailures.add(1);
    businessErrors.add(1);
    orderFlowSuccessRate.add(false);
    return;
  }

  paymentsConfirmed.add(1);
  completedFlows.add(1);
  orderFlowSuccessRate.add(true);
  orderFlowDuration.add(Date.now() - flowStartedAt);

  if (thinkTimeMs > 0) {
    sleep(thinkTimeMs / 1000);
  }
}

export function handleSummary(data) {
  const summary = {
    meta: {
      generatedAt: new Date().toISOString(),
      baseUrl: baseUrl,
      productId: productId,
      paymentType: paymentType,
      orderQuantity: orderQuantity,
      targetVus: targetVus,
      duration: duration,
      scenarioMode: scenarioMode,
      burstRampUp: burstRampUp,
      burstHold: burstHold,
      burstRampDown: burstRampDown,
      summaryFile: summaryFile,
      usePreGeneratedTokens: tokens.length > 0,
    },
    metrics: {
      http_reqs: metricValues(data, 'http_reqs'),
      http_req_failed: metricValues(data, 'http_req_failed'),
      http_req_duration: metricValues(data, 'http_req_duration'),
      iteration_duration: metricValues(data, 'iteration_duration'),
      completed_flows: metricValues(data, 'completed_flows'),
      payments_confirmed: metricValues(data, 'payments_confirmed'),
      order_flow_success_rate: metricValues(data, 'order_flow_success_rate'),
      business_errors: metricValues(data, 'business_errors'),
      sold_out_errors: metricValues(data, 'sold_out_errors'),
      auth_failures: metricValues(data, 'auth_failures'),
      product_lookup_failures: metricValues(data, 'product_lookup_failures'),
      order_create_failures: metricValues(data, 'order_create_failures'),
      payment_confirm_failures: metricValues(data, 'payment_confirm_failures'),
      order_verify_failures: metricValues(data, 'order_verify_failures'),
      stock_negative_rate: metricValues(data, 'stock_negative_rate'),
      login_duration: metricValues(data, 'login_duration'),
      product_lookup_duration: metricValues(data, 'product_lookup_duration'),
      order_create_duration: metricValues(data, 'order_create_duration'),
      payment_confirm_duration: metricValues(data, 'payment_confirm_duration'),
      order_verify_duration: metricValues(data, 'order_verify_duration'),
      order_flow_duration: metricValues(data, 'order_flow_duration'),
    },
  };

  const lines = [
    'k6 order flow summary',
    `summary file: ${summaryFile}`,
    `http reqs/s: ${formatNumber(summary.metrics.http_reqs.rate)}`,
    `http error rate: ${formatNumber(summary.metrics.http_req_failed.rate)}`,
    `completed flows: ${formatNumber(summary.metrics.completed_flows.count, 0)}`,
    `payments confirmed: ${formatNumber(summary.metrics.payments_confirmed.count, 0)}`,
    `order flow success rate: ${formatNumber(summary.metrics.order_flow_success_rate.rate)}`,
    `product lookup failures: ${formatNumber(summary.metrics.product_lookup_failures.count, 0)}`,
    `order create failures: ${formatNumber(summary.metrics.order_create_failures.count, 0)}`,
    `payment confirm failures: ${formatNumber(summary.metrics.payment_confirm_failures.count, 0)}`,
    `order verify failures: ${formatNumber(summary.metrics.order_verify_failures.count, 0)}`,
  ];

  return {
    stdout: `${lines.join('\n')}\n`,
    [summaryFile]: JSON.stringify(summary, null, 2),
  };
}

function getIdentitySource() {
  if (requireTokens && tokens.length === 0) {
    fail('REQUIRE_TOKENS=true but TOKENS_CSV was not loaded.');
  }
  if (tokens.length > 0) {
    return tokens;
  }
  if (users.length > 0) {
    return users;
  }
  fail('Either TOKENS_CSV or USERS_CSV must be provided.');
}

function getIdentityForCurrentVu() {
  const identities = getIdentitySource();
  return identities[(exec.vu.idInTest - 1) % identities.length];
}

function ensureAuth(identity) {
  if (tokens.length > 0) {
    vuSession.accessToken = identity.access_token;
    vuSession.email = identity.email;
    return;
  }

  if (vuSession.accessToken && vuSession.email === identity.email) {
    return;
  }

  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({
      email: identity.email,
      password: identity.password,
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'auth_login' },
      timeout: requestTimeout,
    },
  );
  loginDuration.add(response.timings.duration);

  if (!check(response, { 'login status is 200': (res) => res.status === 200 })) {
    authFailures.add(1);
    fail(`Login failed for ${identity.email}. status=${response.status}`);
  }

  const body = parseJson(response, 'login');
  const accessToken = body && body.data && body.data.accessToken;
  if (!accessToken) {
    authFailures.add(1);
    fail(`Login response for ${identity.email} has no accessToken.`);
  }

  vuSession.accessToken = accessToken;
  vuSession.email = identity.email;
}

function buildOrderRequestId() {
  return `k6-${targetVus}-${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`;
}

function parseJson(response, label) {
  try {
    return response.json();
  } catch (error) {
    fail(`Failed to parse JSON for ${label}. status=${response.status}`);
  }
}

function parseCsv(csvPath, expectedHeaders) {
  if (!csvPath) {
    return [];
  }

  const raw = open(csvPath).trim();
  if (!raw) {
    fail(`CSV is empty: ${csvPath}`);
  }

  const lines = raw
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  if (lines.length < 2) {
    fail(`CSV must contain a header and at least one row: ${csvPath}`);
  }

  const header = lines[0].split(',').map((value) => value.trim().toLowerCase());
  if (header.length !== expectedHeaders.length) {
    fail(`CSV header length mismatch: ${csvPath}`);
  }

  for (let index = 0; index < expectedHeaders.length; index += 1) {
    if (header[index] !== expectedHeaders[index]) {
      fail(`CSV header must be '${expectedHeaders.join(',')}': ${csvPath}`);
    }
  }

  return lines.slice(1).map((line, index) => {
    const parts = line.split(',');
    const row = {};

    expectedHeaders.forEach((columnName, columnIndex) => {
      row[columnName] = parts[columnIndex] ? parts[columnIndex].trim() : '';
    });

    const invalid = expectedHeaders.some((columnName) => !row[columnName]);
    if (invalid) {
      fail(`Invalid CSV row at line ${index + 2}: ${line}`);
    }

    return row;
  });
}

function recordFailure(response) {
  if (response.status >= 400 && response.status < 500) {
    businessErrors.add(1);
    const body = safeBody(response);
    if (body.includes('PRODUCT_SOLD_OUT') || body.includes('sold out')) {
      soldOutErrors.add(1);
    }
  }
}

function safeBody(response) {
  return typeof response.body === 'string' ? response.body : '';
}

function metricValues(data, name) {
  const metric = data.metrics[name];
  return metric && metric.values ? metric.values : {};
}

function buildOptions() {
  const base = {
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    thresholds: {
      stock_negative_rate: ['rate==0'],
    },
  };

  if (scenarioMode === 'burst') {
    base.scenarios = {
      default: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
          { duration: burstRampUp, target: targetVus },
          { duration: burstHold, target: targetVus },
          { duration: burstRampDown, target: 0 },
        ],
        gracefulRampDown: '0s',
      },
    };
    return base;
  }

  base.vus = targetVus;
  base.duration = duration;
  return base;
}

function formatNumber(value, decimals) {
  const fractionDigits = typeof decimals === 'number' ? decimals : 2;
  if (value === undefined || value === null || Number.isNaN(value)) {
    return 'n/a';
  }
  return Number(value).toFixed(fractionDigits);
}
