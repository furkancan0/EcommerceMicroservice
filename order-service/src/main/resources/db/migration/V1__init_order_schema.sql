-- V1__init_order_schema.sql

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS orders (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID         NOT NULL,
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    total_amount     DECIMAL(19,4) NOT NULL,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'USD',
    idempotency_key  VARCHAR(255) UNIQUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    version          BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS order_items (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id     UUID         NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id   UUID         NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity     INT          NOT NULL CHECK (quantity > 0),
    unit_price   DECIMAL(19,4) NOT NULL,
    total_price  DECIMAL(19,4) NOT NULL
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id             UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id   UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count    INT          NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMP,
    error_message  TEXT,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING','PUBLISHED','FAILED'))
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id       VARCHAR(255) PRIMARY KEY,
    processed_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    result_status  VARCHAR(50)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_orders_user_id    ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders(status);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_outbox_pending    ON outbox_events(status, created_at)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_processed_time    ON processed_events(processed_at);
