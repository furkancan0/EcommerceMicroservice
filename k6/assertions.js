/**
 * Run after order-flow.js:
 *   k6 run k6/assertions.js
 */

import http from 'k6/http';
import { check, fail } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_EMAIL    = __ENV.ADMIN_EMAIL    || 'admin@ecommerce.com';
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'Admin123!';

// Products with known initial stock from inventory-init.sql
const PRODUCT_STOCK = {
  '550e8400-e29b-41d4-a716-446655440001': { name: 'Wireless Headphones', initialStock: 800 },
  '550e8400-e29b-41d4-a716-446655440002': { name: 'Mechanical Keyboard',  initialStock: 800  },
  '550e8400-e29b-41d4-a716-446655440003': { name: 'Laptop Stand',          initialStock: 800 },
};

export const options = {
  scenarios: {
    assertions: {
      executor: 'shared-iterations',
      vus: 2,
      iterations: 2,
    },
  },
  thresholds: {
    checks: ['rate==1.00'],   // ALL assertions must pass
  },
};

export default function () {

  // Inventory invariants
  console.log('Checking inventory invariants...');

  for (const [productId, meta] of Object.entries(PRODUCT_STOCK)) {
    const res = http.get(`${BASE_URL}/api/products/${productId}/availability`, {
      timeout: '10s',
    });

    check(res, {
      [`[${meta.name}] availability endpoint responds`]: (r) => r.status === 200,
    });

    if (res.status !== 200) continue;

    const body = JSON.parse(res.body);

    // CRITICAL: available count must never go negative (oversell)
    const noOversell = body.available >= 0;
    check(body, {
      [`[${meta.name}] NO OVERSELL (available >= 0)`]: () => noOversell,
      [`[${meta.name}] available within initial stock range`]:
        (b) => b.available <= meta.initialStock,
    });

    if (!noOversell) {
      console.error(
        `OVERSELL DETECTED: ${meta.name} — available=${body.available} (NEGATIVE)`
      );
    } else {
      const sold = meta.initialStock - body.available;
      console.log(
        ` ${meta.name}: ${body.available}/${meta.initialStock} remaining (${sold} sold)`
      );
    }
  }

  // 2. Rate limiter is functioning (should not crash, just throttle)
  console.log('\nChecking rate limiter behavior...');

  let rateLimitHit = false;
  for (let i = 0; i < 280; i++) {
    const res = http.get(`${BASE_URL}/api/products/550e8400-e29b-41d4-a716-446655440001`, {
      timeout: '5s',
    });
    if (res.status === 429) {
      rateLimitHit = true;
      check(res, {
        '[rate-limit] 429 response is structured JSON': (r) => {
          try { JSON.parse(r.body); return true; } catch { return false; }
        },
      });
      break;
    }
  }
  console.log(rateLimitHit
    ? '   Rate limiter correctly returns 429'
    : '   Rate limiter not triggered (may need higher volume)');

  // 3. Circuit breaker health endpoints
  console.log('\nChecking circuit breaker states...');

  const cbRes = http.get(`${BASE_URL}/actuator/circuitbreakers`, { timeout: '10s' });
  if (cbRes.status === 200) {
    let cbState;
    try { cbState = JSON.parse(cbRes.body); } catch { cbState = null; }

    if (cbState?.circuitBreakers) {
      for (const [name, info] of Object.entries(cbState.circuitBreakers)) {
        const state = info.state;
        console.log(`  CB [${name}]: ${state}`);
        check({ name, state }, {
          [`[CB:${name}] not in FORCED_OPEN state`]: (o) => o.state !== 'FORCED_OPEN',
        });
      }
    }
  }

  // 4. Gateway health
  console.log('\nChecking service health...');

  const healthRes = http.get(`${BASE_URL}/actuator/health`, { timeout: '10s' });
  check(healthRes, {
    '[health] gateway is UP': (r) => {
      if (r.status !== 200) return false;
      try { return JSON.parse(r.body).status === 'UP'; } catch { return false; }
    },
  });

  console.log('  ASSERTIONS COMPLETE');
}
