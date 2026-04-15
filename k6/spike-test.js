/**
 *  Run:
 *    k6 run k6/spike-test.js
 */

import http from 'k6/http';
import { check, group, sleep, fail } from 'k6';
import { Counter, Rate, Trend, Gauge } from 'k6/metrics';
import { SharedArray } from 'k6/data';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL          = __ENV.BASE_URL          || 'http://localhost:8080';
const AUTH_SERVICE_URL  = __ENV.AUTH_SERVICE_URL  || 'http://localhost:8085';
const NUM_USERS         = parseInt(__ENV.NUM_USERS || '500');

// Seeded products (inventory-init.sql)
const PRODUCTS = [
  { id: '550e8400-e29b-41d4-a716-446655440001', name: 'Wireless Headphones', price: 299.99 },
  { id: '550e8400-e29b-41d4-a716-446655440002', name: 'Mechanical Keyboard',  price: 149.99 },
  { id: '550e8400-e29b-41d4-a716-446655440003', name: 'Laptop Stand',          price: 49.99  },
];

const ordersCreated       = new Counter('spike_orders_created');
const ordersConfirmed     = new Counter('spike_orders_confirmed');
const ordersCancelled     = new Counter('spike_orders_cancelled');
const oversellDetected    = new Counter('spike_oversell_detected');
const tokenMissing        = new Counter('spike_token_missing');
const orderSuccessRate    = new Rate('spike_order_success_rate');
const sagaCompletionRate  = new Rate('spike_saga_completion_rate');
const errorRate           = new Rate('spike_error_rate');
const orderCreateDuration = new Trend('spike_order_create_ms', true);
const sagaDuration        = new Trend('spike_saga_ms',         true);
const activeOrders        = new Gauge('spike_active_orders');

export const options = {
  scenarios: {
    spike: {
      executor:         'ramping-arrival-rate',
      startRate:         0,
      timeUnit:          '1s',
      preAllocatedVUs:   100,
      maxVUs:            NUM_USERS,
      stages: [
        { duration: '10s', target: 0    },
        { duration: '10s', target: 200 },
        { duration: '1m',  target: 500 },
        { duration: '20s', target: 200  },
        { duration: '20s', target: 500 },
        { duration: '40s',  target: 500 },
        { duration: '1m', target: 0    },
      ],
    },
  },

  thresholds: {

    http_req_failed:              ['rate<0.10'],
    http_req_duration:            ['p(95)<3000'],

    spike_order_success_rate:     ['rate>0.90'],
    spike_saga_completion_rate:   ['rate>0.80'],
    spike_error_rate:             ['rate<0.10'],

    spike_oversell_detected:      ['count==0'],
  },
};

function jsonPost(url, body, token = null, params = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return http.post(url, JSON.stringify(body), {
    headers,
    timeout: params.timeout || '10s',
    tags:    params.tags    || {},
  });
}

function jsonGet(url, token = null, params = {}) {
  const headers = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return http.get(url, {
    headers,
    timeout: params.timeout || '10s',
    tags:    params.tags    || {},
  });
}

function parseBody(res) {
  try { return JSON.parse(res.body); } catch { return null; }
}

export function setup() {


  // Step 1: Register all users directly (no gateway rate limiter)
  console.log(`\nRegistering ${NUM_USERS} users via ${AUTH_SERVICE_URL}...`);

  const REGISTER_BATCH = 30;
  let registered = 0;
  let alreadyExist = 0;

  for (let start = 0; start < NUM_USERS; start += REGISTER_BATCH) {
    const end      = Math.min(start + REGISTER_BATCH, NUM_USERS);
    const requests = [];

    for (let i = start; i < end; i++) {
      requests.push([
        'POST',
        `${AUTH_SERVICE_URL}/api/auth/register`,
        JSON.stringify({
          email:     `spike-user-${i}@example.com`,
          password:  'SpikeTest123!',
          firstName: 'Spike',
          lastName:  `User${i}`,
        }),
        { headers: { 'Content-Type': 'application/json' }, timeout: '15s' },
      ]);
    }

    const responses = http.batch(requests);
    for (const res of responses) {
      if (res.status === 200)              registered++;
      else if (res.status === 400 || res.status === 409) alreadyExist++;
      // ignore other errors
    }

    sleep(0.1);
  }

  console.log(` ${registered} newly registered, ${alreadyExist} already existed`);

  // Step 2: Login all users and collect tokens
  console.log(`\nLogging in ${NUM_USERS} users...`);

  const LOGIN_BATCH = 30;
  const tokens = new Array(NUM_USERS).fill(null);
  let loginOk = 0;
  let loginFail = 0;

  for (let start = 0; start < NUM_USERS; start += LOGIN_BATCH) {
    const end      = Math.min(start + LOGIN_BATCH, NUM_USERS);
    const requests = [];
    const indices  = [];

    for (let i = start; i < end; i++) {
      indices.push(i);
      requests.push([
        'POST',
        `${AUTH_SERVICE_URL}/api/auth/login`,
        JSON.stringify({
          email:    `spike-user-${i}@example.com`,
          password: 'SpikeTest123!',
        }),
        { headers: { 'Content-Type': 'application/json' }, timeout: '15s' },
      ]);
    }

    const responses = http.batch(requests);

    for (let j = 0; j < responses.length; j++) {
      const res   = responses[j];
      const idx   = indices[j];

      if (res.status === 200) {
        let body;
        try { body = JSON.parse(res.body); } catch { body = null; }

        if (body?.accessToken) {
          // Store only what the VU needs — no refreshToken, no PII
          tokens[idx] = {
            token:  body.accessToken,
            userId: body.userId,
            email:  `spike-user-${idx}@example.com`,
          };
          loginOk++;
        } else {
          loginFail++;
        }
      } else {
        loginFail++;
        if (loginFail <= 3) {
          console.warn(`Login failed for spike-user-${idx}: HTTP ${res.status}`);
        }
      }
    }

    sleep(0.1);
  }

  const readyPct = ((loginOk / NUM_USERS) * 100).toFixed(1);
  console.log(` ${loginOk}/${NUM_USERS} tokens obtained (${readyPct}%)`);

  if (loginOk < NUM_USERS * 0.90) {
    fail(
      `Only ${loginOk}/${NUM_USERS} tokens ready (${readyPct}%). ` +
      `Ensure auth-service is reachable at ${AUTH_SERVICE_URL}`
    );
  }

  //  Step 3: Health check gateway
  const health = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
  if (health.status !== 200) {
    fail(`Gateway not healthy at ${BASE_URL} (HTTP ${health.status})`);
  }

  console.log(`\n Setup complete — ${loginOk} tokens ready. Starting spike...\n`);

  return { tokens };
}

export default function (data) {
  const vuIndex = (__VU - 1) % data.tokens.length;
  const auth    = data.tokens[vuIndex];

  if (!auth) {
    tokenMissing.add(1);
    return;
  }

  // Pick a product deterministically per VU (spreads load across products)
  const product = PRODUCTS[vuIndex % PRODUCTS.length];

  // Create order
  // The gateway's JwtAuthFilter reads the Bearer token and injects X-User-Id
  const idempotencyKey = uuidv4();
  const orderStart     = Date.now();

  const orderRes = http.post(
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
        'Content-Type':    'application/json',
        'Authorization':   `Bearer ${auth.token}`,
        'Idempotency-Key': idempotencyKey,
      },
      timeout: '8s',
      tags:    { name: 'spike_create_order' },
    }
  );

  orderCreateDuration.add(Date.now() - orderStart);

  const orderOk = check(orderRes, {
    '[spike] order accepted (202)': (r) => r.status === 202,
    '[spike] no 5xx error':         (r) => r.status < 500,
    '[spike] has order id':         (r) => {
      try { return !!JSON.parse(r.body).id; } catch { return false; }
    },
  });

  orderSuccessRate.add(orderOk ? 1 : 0);
  errorRate.add(orderOk ? 0 : 1);

  if (!orderOk) return;

  const orderBody = parseBody(orderRes);
  if (!orderBody?.id) return;

  ordersCreated.add(1);
  activeOrders.add(1);

  const orderId = orderBody.id;

  const sagaStart = Date.now();
  let finalStatus = null;

  for (let attempt = 0; attempt < 3; attempt++) {
    sleep(2);

    const pollRes  = jsonGet(
      `${BASE_URL}/api/orders/${orderId}`,
      auth.token,
      { tags: { name: 'spike_poll_order' } }
    );
    const pollBody = parseBody(pollRes);

    if (pollRes.status === 200 && pollBody?.status) {
      const s = pollBody.status;
      if (s === 'CONFIRMED' || s === 'CANCELLED' || s === 'PAYMENT_FAILED') {
        finalStatus = s;
        break;
      }
    }
  }

  if (finalStatus) {
    sagaDuration.add(Date.now() - sagaStart);
    activeOrders.add(-1);

    if (finalStatus === 'CONFIRMED') {
      ordersConfirmed.add(1);
      sagaCompletionRate.add(1);
    } else {
      ordersCancelled.add(1);
      sagaCompletionRate.add(0);
    }
  } else {
    sagaCompletionRate.add(0);
    activeOrders.add(-1);
  }
}

export function teardown(data) {

  let oversold = false;

  for (const product of PRODUCTS) {
    const res  = jsonGet(`${BASE_URL}/api/products/${product.id}/availability`);
    const body = parseBody(res);

    if (res.status !== 200 || body === null) {
      console.warn(`  Could not check ${product.name} (HTTP ${res.status})`);
      continue;
    }

    const available = body.available ?? -999;
    const status    = available < 0 ? ' OVERSOLD' : 'OK';

    console.log(`  ${status}  ${product.name}: ${available} units remaining`);

    if (available < 0) {
      oversellDetected.add(1);
      oversold = true;
      console.error(
        `  OVERSELL DETECTED: ${product.name} available=${available}.\n` +
        `  SELECT FOR UPDATE or distributed lock may have failed under spike.`
      );
    }
  }

  if (!oversold) {
    console.log('\n No overselling detected — stock integrity maintained under spike');
  }
}

export function handleSummary(data) {
  const m    = data.metrics || {};
  const v    = (name, stat) => m[name]?.values?.[stat] ?? 0;
  const rate = (name)       => (v(name, 'rate') * 100).toFixed(1) + '%';
  const cnt  = (name)       => v(name, 'count');
  const ms   = (name, pct)  => v(name, pct).toFixed(0) + 'ms';

  const allThresholds = Object.values(data.thresholds ?? {})
    .every(t => !t.ok === false);

  const banner = allThresholds ? 'SPIKE TEST PASSED' : ' SPIKE TEST FAILED';

  console.log(`
  ${banner.padEnd(60)}║

  Orders created        : ${String(cnt('spike_orders_created')).padEnd(36)}
  Orders confirmed      : ${String(cnt('spike_orders_confirmed')).padEnd(36)}
  Orders cancelled      : ${String(cnt('spike_orders_cancelled')).padEnd(36)}
  Oversell detected     : ${String(cnt('spike_oversell_detected')).padEnd(36)}

  Order success rate    : ${rate('spike_order_success_rate').padEnd(36)}
  Saga completion rate  : ${rate('spike_saga_completion_rate').padEnd(36)}
  Error rate            : ${rate('spike_error_rate').padEnd(36)}

  Order create  p50/p95 : ${(ms('spike_order_create_ms','p(50)') + ' / ' + ms('spike_order_create_ms','p(95)')).padEnd(36)}
  Saga          p50/p95 : ${(ms('spike_saga_ms','p(50)') + ' / ' + ms('spike_saga_ms','p(95)')).padEnd(36)}
  HTTP          p50/p95 : ${(ms('http_req_duration','p(50)') + ' / ' + ms('http_req_duration','p(95)')).padEnd(36)}
`);

  return {};
}
