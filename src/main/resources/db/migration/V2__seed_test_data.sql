INSERT INTO products (
    id,
    company_name,
    category,
    name,
    model_version,
    price_nok,
    product_url,
    width_cm,
    height_cm,
    depth_cm,
    model_key,
    created_at,
    updated_at
) VALUES (
    'c3e2a1b0-5d4f-4e3a-8b7c-6f5e4d3c2b1a',
    'Bohus',
    'CHAIR',
    'Noni Spisestol',
    1,
    1599.00,
    'https://www.bohus.no/stoler/spisestoler/noni-spisestol-1',
    50,
    85,
    50,
    'models/c3e2a1b0-5d4f-4e3a-8b7c-6f5e4d3c2b1a/v1.usdz',
    now(),
    now()
) ON CONFLICT DO NOTHING;
