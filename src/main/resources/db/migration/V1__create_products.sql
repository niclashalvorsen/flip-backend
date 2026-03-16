CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE products (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    company_name  VARCHAR(255)  NOT NULL,
    category      VARCHAR(50)   NOT NULL,
    name          VARCHAR(255)  NOT NULL,
    model_version INTEGER       NOT NULL DEFAULT 1,
    price_nok     DECIMAL(10,2) NOT NULL,
    product_url   TEXT,
    width_cm      DECIMAL(8,2)  NOT NULL DEFAULT 0,
    height_cm     DECIMAL(8,2)  NOT NULL DEFAULT 0,
    depth_cm      DECIMAL(8,2)  NOT NULL DEFAULT 0,
    model_key     VARCHAR(500),
    thumbnail_key VARCHAR(500),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category ON products (category);
CREATE INDEX idx_products_search ON products USING gin (
    to_tsvector('simple', name || ' ' || company_name)
);
