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
```
## Core Design Decisions

 1. Transactional Outbox Pattern

 2. Idempotent Consumers

 3. Retry Topics + DLQ

 4. Circuit Breaker (Payment Providers)


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

### 6. Run Smoke Tests

```bash
chmod +x scripts/test-saga.sh
./scripts/test-saga.sh
```
## Technology Stack

| Spring Boot 3.3.0    
| Spring Cloud Gateway API Gateway + rate limiting  
| Spring Kafka Event streaming  
| Spring Data JPA ORM   
| Spring Security Auth service security  
| Spring Retry @RetryableTopic   
| Resilience4j  Circuit breaker + retry   
| Redisson Distributed locks   
| JJWT RS256 JWT   
| PostgreSQL Per-service databases   
| Apache Kafka Event bus   
| Redis Cache + locks + rate limit   
| Flyway DB schema migrations   
| Lombok Boilerplate reduction   
| Java 21 Runtime   
| Docker Containerization   
