-- Inventory Service Schema
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(255) NOT NULL,
    sku VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    price DECIMAL(19, 4) NOT NULL,
    category VARCHAR(100),
    image_url VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE inventory (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES products(id) UNIQUE,
    total_quantity INT NOT NULL DEFAULT 0 CHECK (total_quantity >= 0),
    reserved_quantity INT NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    available_quantity INT GENERATED ALWAYS AS (total_quantity - reserved_quantity) STORED,
    low_stock_threshold INT NOT NULL DEFAULT 10,
    version BIGINT NOT NULL DEFAULT 0,  -- optimistic locking
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_reservation CHECK (reserved_quantity <= total_quantity)
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INT NOT NULL CHECK (quantity > 0),
    status VARCHAR(30) NOT NULL DEFAULT 'RESERVED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    released_at TIMESTAMP,
    CONSTRAINT chk_status CHECK (status IN ('RESERVED','CONFIRMED','RELEASED','EXPIRED'))
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

-- Indexes
CREATE INDEX idx_inventory_product_id ON inventory(product_id);
CREATE INDEX idx_reservations_order_id ON reservations(order_id);
CREATE INDEX idx_reservations_status ON reservations(status);
CREATE INDEX idx_outbox_status ON outbox_events(status) WHERE status = 'PENDING';
CREATE INDEX idx_products_category ON products(category);
CREATE INDEX idx_products_sku ON products(sku);

-- Seed sample products
INSERT INTO products (id, name, sku, description, price, category) VALUES
  ('550e8400-e29b-41d4-a716-446655440001', 'Wireless Headphones', 'WH-001', 'Premium noise-canceling headphones', 299.99, 'Electronics'),
  ('550e8400-e29b-41d4-a716-446655440002', 'Mechanical Keyboard', 'KB-001', 'RGB Mechanical Gaming Keyboard', 149.99, 'Electronics'),
  ('550e8400-e29b-41d4-a716-446655440003', 'Laptop Stand', 'LS-001', 'Adjustable aluminum laptop stand', 49.99, 'Accessories');

INSERT INTO inventory (product_id, total_quantity, reserved_quantity, low_stock_threshold) VALUES
  ('550e8400-e29b-41d4-a716-446655440001', 100, 0, 10),
  ('550e8400-e29b-41d4-a716-446655440002', 50, 0, 5),
  ('550e8400-e29b-41d4-a716-446655440003', 200, 0, 20);
