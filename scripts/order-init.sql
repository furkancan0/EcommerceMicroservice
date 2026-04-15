-- Order Service Schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0  -- optimistic locking
);

CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price DECIMAL(19, 4) NOT NULL,
    total_price DECIMAL(19, 4) NOT NULL
);

-- Outbox table for transactional outbox pattern
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, PUBLISHED, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP,
    error_message TEXT,
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Idempotency store for consumer deduplication
CREATE TABLE processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    result_status VARCHAR(50)
);

-- Indexes
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_outbox_status ON outbox_events(status) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_created ON outbox_events(created_at);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);

-- Cleanup job: remove old processed events after 7 days
CREATE OR REPLACE FUNCTION cleanup_processed_events() RETURNS void AS $$
BEGIN
    DELETE FROM processed_events WHERE processed_at < NOW() - INTERVAL '7 days';
END;
$$ LANGUAGE plpgsql;
