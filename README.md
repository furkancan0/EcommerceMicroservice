# E-Commerce Platform

> **Spring Boot 3.3 · PostgreSQL 16 · Apache Kafka · Redis · Resilience4j**  
> Microservices · Choreography Saga · Event-Driven · Eventually Consistent
---
## Architecture Overview

```
                         ┌──────────────────────────────────┐
                         │         API GATEWAY :8080         │
                         │  Spring Cloud Gateway             │
                         │  • JWT validation (RSA public key)│
                         │  • Rate limiting (Redis token-    │
                         │    bucket, per-user)     │
                         │  • Circuit breaker (Resilience4j) │
                         │  • Request enrichment             │
                         └───┬───┬─── ───┬───────────────────┘
                             │   │       │
            ┌────────────────┘   │       └────────────────────┐
            ▼                    ▼                            ▼
 ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────────┐
 │  Auth Service    │  │  Order Service  │  │  Inventory Service   │
 │  :8085           │  │  :8081          │  │  :8083               │
 │  • Register/Login│  │  • Create order │  │  • Product catalog   │
 │  • RSA JWT issue │  │  • Saga start   │  │  • Stock reservation │
 │  • Token refresh │  │  • Saga react   │  │  • Cache-aside Redis │
 │  • Blacklist     │  │  • Outbox relay │  │  • Outbox relay      │
 └────────┬─────────┘  └──────┬──────────┘  └──────────┬───────────┘
          │                   │                        │
          │           ┌───────┘                        │
          ▼           ▼                                ▼
     ┌─────────┐  ┌──────────────┐              ┌──────────────────┐
     │ authdb  │  │  orderdb     │              │  inventorydb     │
     │ Postgres│  │  Postgres    │              │  Postgres        │
     └─────────┘  └──────────────┘              └──────────────────┘

                         ┌───────────────────┐
                         │  Payment Service  │
                         │  :8082            │
                         │  • Charge via CB  │
                         │  • PayPal/Stripe  │
                         │  • Outbox relay   │
                         └──────────┬────────┘
                                    │
                              ┌─────┘
                              ▼
                         ┌──────────┐
                         │paymentdb │
                         │Postgres  │
                         └──────────┘

 ┌─────────────────────────────────────────────────────────────────┐
 │                      Apache Kafka                               │
 │  order.events       order.events.retry-0/1/2  order.events.dlq  │
 │  payment.events     payment.events.retry-0/1  payment.events.dlq│
 │  inventory.events   inventory.events.retry  inventory.events.dlq│
 │  notification.events                                            │
 └─────────────────────────────────────────────────────────────────┘

                    ┌────────────────────────┐
                    │  Notification Service  │
                    │  :8084                 │
                    │  • Email (JavaMail)    │
                    │  • Redis dedup (SET NX)│
                    └────────────────────────┘

                    ┌────────────────────────┐
                    │  Redis :6379           │
                    │  • Rate limiting       │
                    │  • Distributed locks   │
                    │  • JWT blacklist       │
                    │  • Product cache       │
                    │  • Notification        │
                    └────────────────────────┘
```
## Core Design Decisions

### 1. Transactional Outbox Pattern

**Problem:** Publishing directly to Kafka inside a DB transaction is unreliable.  
If Kafka is down when the transaction commits → event is lost.  
If the service crashes after Kafka publish but before DB commit → ghost event.

**Solution:** Every event is written to an `outbox_events` table **in the same DB transaction** as the business operation. A background scheduler polls this table every 500ms and relays PENDING events to Kafka.

### 2. SELECT FOR UPDATE SKIP LOCKED (Multi-Instance Safety)

The outbox relay uses `SELECT ... FOR UPDATE SKIP LOCKED` so multiple service instances don't process the same outbox row concurrently.

The same pattern is used for inventory reservation — each product row gets `FOR UPDATE` before modifying stock.

### 3. Distributed Locks (Redisson)

- **Order creation:** lock on `idempotency-key` prevents two concurrent identical requests both slipping through the DB check.
- **Inventory reservation:** lock on `product-id` serializes reservation attempts before the DB lock (reduces DB contention).
- **Payment:** lock on `order-id` prevents two payment attempts for the same order.

### 4. Idempotent Consumers

Kafka guarantees **at-least-once** delivery. Consumers use a `processed_events` table to detect and skip duplicates:

The `INSERT ... ON CONFLICT DO NOTHING` SQL ensures this is atomic even under concurrent retries.

### 5. Retry Topics + DLQ

### 6. Circuit Breaker (Payment Providers)
CLOSED (normal) -> OPEN (fail-fast) -> HALF-OPEN (probe)

### 7. Caching Strategy (Redis)

| Product catalog | Cache-aside | 10 min | Evicted on update |
| Category listings | Cache-aside | 5 min | Evicted on update |
| Available count (display) | Cache-aside | 30 sec | for reservation |
| JWT blacklist | Redis SET + TTL | Token remaining lifetime | Set on logout |
| Notification | Redis SET NX + TTL | 24 hours | Prevents duplicate emails |
| Rate limit state | Sliding window (gateway) | Auto (per window) | Per-user|

### 8. JWT with RSA Key Pair

### Prerequisites
- Docker & Docker Compose
- Java 21 (for local builds)
- Maven 3.9+

### 1. Generate RSA Keys

```bash
chmod +x scripts/generate-keys.sh
./scripts/generate-keys.sh
```

### 2. Start Everything

```bash
docker-compose up --build -d
```

### 3. Verify Health

```bash
# Check all services are UP
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # Orders
curl http://localhost:8082/actuator/health   # Payments
curl http://localhost:8083/actuator/health   # Inventory
curl http://localhost:8085/actuator/health   # Auth

### 6. Run Smoke Tests

```bash
chmod +x scripts/test-saga.sh
./scripts/test-saga.sh
```
## Technology Stack

| Spring Boot | 3.3.0  
| Spring Cloud Gateway | API Gateway + rate limiting  
| Spring Kafka |Event streaming  
| Spring Data JPA | ORM   
| Spring Security | Auth service security  
| Spring Retry | @RetryableTopic   
| Resilience4j  | Circuit breaker + retry   
| Redisson | Distributed locks   
| JJWT | RS256 JWT   
| PostgreSQL | Per-service databases   
| Apache Kafka | Event bus   
| Redis | Cache + locks + rate limit   
| Flyway | DB schema migrations   
| Lombok | Boilerplate reduction   
| Java 21 | Runtime   
| Docker | Containerization   
