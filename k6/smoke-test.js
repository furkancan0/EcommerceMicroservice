/**
 *  Run:
 *    k6 run k6/smoke-test.js
 */

import http from 'k6/http';
import { check, group, sleep, fail } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const PRODUCTS = {
  headphones: {
    id:    '550e8400-e29b-41d4-a716-446655440001',
    name:  'Wireless Headphones',
    price: 299.99,
    stock: 800,
  },
  keyboard: {
    id:    '550e8400-e29b-41d4-a716-446655440002',
    name:  'Mechanical Keyboard',
    price: 149.99,
    stock: 800,
  },
  laptopStand: {
    id:    '550e8400-e29b-41d4-a716-446655440003',
    name:  'Laptop Stand',
    price: 49.99,
    stock: 800,
  },
};


export const options = {
  scenarios: {
    smoke: {
      executor:   'per-vu-iterations',
      vus:         1,
      iterations:  1,
      maxDuration: '3m',
    },
  },
  thresholds: {
    checks:            ['rate==1.00'],    // EVERY check must pass
    http_req_failed:   ['rate==0'],       // ZERO HTTP errors
    http_req_duration: ['p(95)<5000'],    // all requests < 5s
  },
};


const JSON_HEADERS = { 'Content-Type': 'application/json' };

function authHeaders(token) {
  return {
    'Content-Type':  'application/json',
    'Authorization': `Bearer ${token}`,
  };
}

function post(path, body, token = null) {
  return http.post(
    `${BASE_URL}${path}`,
    JSON.stringify(body),
    { headers: token ? authHeaders(token) : JSON_HEADERS, timeout: '15s' }
  );
}

function get(path, token = null) {
  return http.get(
    `${BASE_URL}${path}`,
    { headers: token ? authHeaders(token) : {}, timeout: '15s' }
  );
}

function parseBody(res) {
  try   { return JSON.parse(res.body); }
  catch { return null; }
}

function log(icon, msg) {
  console.log(`  ${icon} ${msg}`);
}

function stepRegister() {
  const email    = `smoke-${uuidv4().slice(0, 8)}@ecommerce-test.com`;
  const password = 'SmokeTest123!';

  log('👤', `Registering: ${email}`);

  const res  = post('/api/auth/register', {
    email,
    password,
    firstName: 'Smoke',
    lastName:  'Test',
  });
  const body = parseBody(res);

  check(res, {
    '[register] HTTP 200':           (r) => r.status === 200,
  });
  check(body, {
    '[register] body is not null':       (b) => b !== null,
    '[register] has accessToken':        (b) => typeof b?.accessToken === 'string' && b.accessToken.length > 0,
    '[register] has refreshToken':       (b) => typeof b?.refreshToken === 'string',
    '[register] has userId (UUID)':      (b) => /^[0-9a-f-]{36}$/.test(b?.userId),
    '[register] has email':              (b) => b?.email === email,
    '[register] has role USER':          (b) => b?.role === 'USER',
    '[register] tokenType is Bearer':    (b) => b?.tokenType === 'Bearer',
    '[register] expiresIn > 0':          (b) => (b?.expiresIn ?? 0) > 0,
  });

  if (res.status !== 200 || !body?.accessToken) {
    fail(`Registration failed (HTTP ${res.status}): ${res.body}`);
  }

  log('', `Registered userId=${body.userId}`);
  return { token: body.accessToken, refreshToken: body.refreshToken,
           userId: body.userId, email };
}

function stepLogin(email, password) {
  log('', `Logging in: ${email}`);

  const res  = post('/api/auth/login', { email, password });
  const body = parseBody(res);

  check(res, {
    '[login] HTTP 200':         (r) => r.status === 200,
  });
  check(body, {
    '[login] has accessToken':  (b) => typeof b?.accessToken === 'string' && b.accessToken.length > 0,
    '[login] has userId':       (b) => /^[0-9a-f-]{36}$/.test(b?.userId),
    '[login] email matches':    (b) => b?.email === email,
  });

  if (res.status !== 200 || !body?.accessToken) {
    fail(`Login failed (HTTP ${res.status}): ${res.body}`);
  }

  log('', `Login OK — token starts with: ${body.accessToken.slice(0, 20)}...`);
  return body.accessToken;
}

function stepBrowseProducts() {
  log('🛍️', 'Browsing product catalog...');

  // GET /api/products/{productId} — public endpoint, no auth
  const product = PRODUCTS.headphones;
  const res     = get(`/api/products/${product.id}`);
  const body    = parseBody(res);

  check(res, {
    '[product-detail] HTTP 200':       (r) => r.status === 200,
  });
  check(body, {
    '[product-detail] has id':         (b) => b?.id === product.id,
    '[product-detail] has name':       (b) => typeof b?.name === 'string',
    '[product-detail] has price':      (b) => (b?.price ?? 0) > 0,
    '[product-detail] has sku':        (b) => typeof b?.sku === 'string',
    '[product-detail] isActive true':  (b) => b?.isActive === true,
  });

  if (res.status !== 200) {
    log('', `Product detail failed: ${res.status} — ${res.body}`);
    return null;
  }

  log('', `Product: ${body.name} — $${body.price}`);

  // GET /api/products/{productId}/availability — public endpoint, no auth
  const availRes  = get(`/api/products/${product.id}/availability`);
  const availBody = parseBody(availRes);

  check(availRes, {
    '[availability] HTTP 200':           (r) => r.status === 200,
  });
  check(availBody, {
    '[availability] has available field': (b) => typeof b?.available === 'number',
    '[availability] available >= 0':      (b) => (b?.available ?? -1) >= 0,
    '[availability] has productId':       (b) => b?.productId === product.id,
  });

  log('', `Stock: ${availBody?.available ?? 'unknown'} units available`);
  return body;
}

function stepCreateOrder(token) {
  const product        = PRODUCTS.headphones;
  const idempotencyKey = uuidv4();

  log('', `Creating order for "${product.name}" — idempotencyKey=${idempotencyKey}`);

  // POST /api/orders
  // Gateway injects X-User-Id from JWT — k6 only sends Authorization header
  const res = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      totalAmount: product.price,
      currency:    'USD',
      items: [{
        productId:   product.id,
        productName: product.name,
        quantity:    1,
        unitPrice:   product.price,
      }],
    }),
    {
      headers: {
        ...authHeaders(token),
        'Idempotency-Key': idempotencyKey,   // required header
      },
      timeout: '15s',
    }
  );
  const body = parseBody(res);

  check(res, {
    '[create-order] HTTP 202 Accepted':  (r) => r.status === 202,
  });
  check(body, {
    '[create-order] has order id':       (b) => /^[0-9a-f-]{36}$/.test(b?.id),
    '[create-order] status is PENDING':  (b) => b?.status === 'PENDING',
    '[create-order] correct amount':     (b) => parseFloat(b?.totalAmount) === product.price,
    '[create-order] currency is USD':    (b) => b?.currency === 'USD',
    '[create-order] has items array':    (b) => Array.isArray(b?.items) && b.items.length === 1,
    '[create-order] item productId':     (b) => b?.items?.[0]?.productId === product.id,
  });

  if (res.status !== 202 || !body?.id) {
    fail(`Order creation failed (HTTP ${res.status}): ${res.body}`);
  }

  log('', `Order created: id=${body.id} status=${body.status}`);
  return { orderId: body.id, idempotencyKey };
}

function stepVerifyIdempotency(token, originalOrderId, idempotencyKey) {
  log('', `Replaying order with same Idempotency-Key to test dedup...`);

  const product = PRODUCTS.headphones;

  const res  = http.post(
    `${BASE_URL}/api/orders`,
    JSON.stringify({
      totalAmount: product.price,
      currency:    'USD',
      items: [{
        productId:   product.id,
        productName: product.name,
        quantity:    1,
        unitPrice:   product.price,
      }],
    }),
    {
      headers: {
        ...authHeaders(token),
        'Idempotency-Key': idempotencyKey,  // same key as original
      },
      timeout: '15s',
    }
  );
  const body = parseBody(res);

  check(res, {
    '[idempotency] HTTP 202 (not 4xx)': (r) => r.status === 202,
  });
  check(body, {
    '[idempotency] returns SAME orderId': (b) => {
      const same = b?.id === originalOrderId;
      if (!same) {
        log('🚨', `IDEMPOTENCY BROKEN: original=${originalOrderId} duplicate=${b?.id}`);
      }
      return same;
    },
  });

  log('', `Idempotency OK — returned same orderId=${body?.id}`);
}

function stepPollSaga(token, orderId) {
  const TERMINAL     = new Set(['CONFIRMED', 'CANCELLED', 'PAYMENT_FAILED']);
  const TIMEOUT_MS   = 60_000;
  const POLL_MS      = 4_000;
  const startMs      = Date.now();

  log('⏳', `Polling order ${orderId} for saga completion (timeout=60s)...`);

  while (Date.now() - startMs < TIMEOUT_MS) {
    // GET /api/orders/{orderId}
    // X-User-Id injected by gateway from JWT — k6 only sends Authorization header
    const res  = get(`/api/orders/${orderId}`, token);
    const body = parseBody(res);

    check(res, {
      '[poll] GET order returns 200': (r) => r.status === 200,
    });
    check(body, {
      '[poll] order id matches':   (b) => b?.id === orderId,
      '[poll] has status field':   (b) => typeof b?.status === 'string',
      '[poll] has userId field':   (b) => /^[0-9a-f-]{36}$/.test(b?.userId),
    });

    if (res.status !== 200 || !body) {
      log('⚠️', `Poll failed HTTP ${res.status} — retrying...`);
      sleep(POLL_MS / 1000);
      continue;
    }

    const elapsed = ((Date.now() - startMs) / 1000).toFixed(1);
    log('🔄', `  [${elapsed}s] status=${body.status}`);

    if (TERMINAL.has(body.status)) {
      return body;
    }

    sleep(POLL_MS / 1000);
  }

  fail(`Saga timed out after ${TIMEOUT_MS / 1000}s — order ${orderId} never reached terminal state`);
  return null;
}

function stepValidateFinalOrder(finalOrder) {
  log('🔍', `Validating final order state: status=${finalOrder?.status}`);

  check(finalOrder, {
    '[saga] order reached terminal state': (o) =>
      ['CONFIRMED', 'CANCELLED', 'PAYMENT_FAILED'].includes(o?.status),

    // Happy path: with test Stripe token the payment should succeed
    '[saga] order is CONFIRMED': (o) => o?.status === 'CONFIRMED',

    '[saga] has createdAt':  (o) => o?.createdAt != null,
    '[saga] has updatedAt':  (o) => o?.updatedAt != null,
    '[saga] has userId':     (o) => /^[0-9a-f-]{36}$/.test(o?.userId),
    '[saga] has totalAmount':(o) => parseFloat(o?.totalAmount) > 0,
    '[saga] has items':      (o) => Array.isArray(o?.items) && o.items.length > 0,
  });

  if (finalOrder?.status === 'CONFIRMED') {
    log('', `Saga COMPLETED — order CONFIRMED`);
  } else {
    log('️', `Saga ended with: ${finalOrder?.status} (check payment service config)`);
  }
}

function stepVerifyInventory(expectedMaxStock) {
  log('', 'Verifying inventory after order...');

  const product  = PRODUCTS.headphones;
  const res      = get(`/api/products/${product.id}/availability`);
  const body     = parseBody(res);

  check(res,  { '[inventory-after] HTTP 200': (r) => r.status === 200 });
  check(body, {
    '[inventory-after] available is a number': (b) => typeof b?.available === 'number',
    '[inventory-after] no oversell (>= 0)':    (b) => (b?.available ?? -1) >= 0,
    '[inventory-after] stock decreased':       (b) => (b?.available ?? expectedMaxStock) < expectedMaxStock,
  });

  log('', `Inventory: ${body?.available} units remaining (was ${expectedMaxStock})`);
}

function stepLogout(token, refreshToken) {
  log('🚪', 'Logging out...');

  const res = post('/api/auth/logout', { refreshToken }, token);

  check(res, {
    '[logout] HTTP 204 No Content': (r) => r.status === 204,
  });

  // Verify blacklist: same token should now be rejected
  sleep(0.5);
  const postLogoutRes = get(`/api/orders`, token);

  check(postLogoutRes, {
    '[logout] blacklisted token returns 401': (r) => r.status === 401,
  });

  log('', `Logout OK — token blacklisted (${postLogoutRes.status})`);
}

export default function () {
  console.log('\n' + '═'.repeat(62));
  console.log('  E-Commerce Smoke Test — Full Saga Flow');
  console.log('  Target: ' + BASE_URL);
  console.log('═'.repeat(62) + '\n');

  // Step 1: Register
  let auth;
  group('1 — Register', function () {
    auth = stepRegister();
  });
  sleep(0.3);

  // Step 2: Login
  let loginToken;
  group('2 — Login', function () {
    loginToken = stepLogin(auth.email, 'SmokeTest123!');
    auth.token = loginToken;
  });
  sleep(0.3);

  // Step 3: Browse product catalog
  let productAtStart;
  group('3 — Browse Products', function () {
    productAtStart = stepBrowseProducts();
  });
  sleep(0.5);

  // Step 4: Create order
  let order;
  group('4 — Create Order', function () {
    order = stepCreateOrder(auth.token);
  });
  sleep(0.3);

  // Step 5: Test idempotency
  group('5 — Idempotency Replay', function () {
    stepVerifyIdempotency(auth.token, order.orderId, order.idempotencyKey);
  });
  sleep(0.5);

  // Step 6: Poll saga until terminal
  let finalOrder;
  group('6 — Poll Saga Completion', function () {
    finalOrder = stepPollSaga(auth.token, order.orderId);
  });

  // Step 7: Validate outcome
  group('7 — Validate Final State', function () {
    stepValidateFinalOrder(finalOrder);
  });
  sleep(0.3);

  // Step 8: Verify inventory
  group('8 — Inventory Integrity', function () {
    const initialStock = PRODUCTS.headphones.stock; // 100 from seed data
    stepVerifyInventory(initialStock);
  });
  sleep(0.3);

  // Step 9: Logout
  group('9 — Logout & Token Blacklist', function () {
    stepLogout(auth.token, auth.refreshToken);
  });

  console.log('\n' + '═'.repeat(62));
  console.log('  Smoke Test Complete');
  console.log('═'.repeat(62) + '\n');
}

export function setup() {
  console.log(`\nPre-flight check: ${BASE_URL}`);

  const healthRes = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
  if (healthRes.status !== 200) {
    fail(
      `Gateway is not healthy (HTTP ${healthRes.status}).\n` +
      `Make sure all services are running:\n` +
      `  docker-compose up -d\n` +
      `  ./scripts/generate-keys.sh`
    );
  }

  let gatewayBody;
  try { gatewayBody = JSON.parse(healthRes.body); } catch { gatewayBody = {}; }

  if (gatewayBody.status !== 'UP') {
    fail(`Gateway status is ${gatewayBody.status}, expected UP`);
  }

  // Verify seeded products are accessible
  const productRes = http.get(
    `${BASE_URL}/api/products/${PRODUCTS.headphones.id}`,
    { timeout: '10s' }
  );

  if (productRes.status !== 200) {
    fail(
      `Seeded product not found (HTTP ${productRes.status}).\n` +
      `Run the inventory DB migration — check inventory-init.sql was applied.`
    );
  }

  console.log('✅ Pre-flight passed — services are up and products are seeded\n');
}

export function handleSummary(data) {
  const checks  = data.metrics?.checks?.values ?? {};
  const total   = checks.passes + checks.fails;
  const passed  = checks.passes;
  const failed  = checks.fails;
  const allPass = failed === 0;

  const reqDuration = data.metrics?.http_req_duration?.values ?? {};
  const p95         = reqDuration['p(95)'] ?? 0;

  console.log(`${allPass ? ' SMOKE TEST PASSED' : ' SMOKE TEST FAILED'}${' '.repeat(allPass ? 39 : 39)}`);
  console.log(` Checks : ${String(passed).padStart(3)} passed, ${String(failed).padStart(3)} failed (${total} total)`);
  console.log(` Latency : p95 = ${p95.toFixed(0).padStart(5)} ms                                 `);

  return {
    stdout: '',
  };
}
