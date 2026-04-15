-- Payment Service Schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL UNIQUE,
    user_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    provider VARCHAR(50) NOT NULL,           -- PAYPAL, STRIPE
    provider_transaction_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    failure_reason TEXT,
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_status CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED','REFUNDED'))
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_id UUID NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    published_at TIMESTAMP,
    error_message TEXT
);

CREATE TABLE processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    result_status VARCHAR(50)
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_outbox_status ON outbox_events(status) WHERE status = 'PENDING';
CREATE INDEX idx_processed_events_time ON processed_events(processed_at);
