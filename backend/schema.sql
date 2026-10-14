CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid TEXT NOT NULL UNIQUE,
    phone TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL DEFAULT 'PayCeylon User',
    handle TEXT NOT NULL UNIQUE,
    email TEXT,
    photo_url TEXT,
    photo_data TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS wallet_balances (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset TEXT NOT NULL CHECK (asset IN ('LKR', 'USDT', 'BTC', 'ETH', 'SOL')),
    balance NUMERIC(24, 8) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    PRIMARY KEY (user_id, asset)
);

CREATE TABLE IF NOT EXISTS payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('CARD', 'BANK')),
    name TEXT NOT NULL,
    brand TEXT,
    last4 CHAR(4) NOT NULL,
    holder TEXT NOT NULL,
    detail TEXT NOT NULL,
    account_type TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    direction TEXT NOT NULL CHECK (direction IN ('OUTGOING', 'INCOMING', 'SWAP', 'TOP_UP', 'WITHDRAWAL')),
    currency TEXT NOT NULL CHECK (currency IN ('LKR', 'USDT', 'BTC', 'ETH', 'SOL')),
    amount NUMERIC(24, 8) NOT NULL CHECK (amount > 0),
    fee NUMERIC(24, 8),
    fee_currency TEXT,
    wallet TEXT NOT NULL CHECK (wallet IN ('FIAT', 'CRYPTO')),
    status TEXT NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    reference TEXT NOT NULL UNIQUE,
    counterparty_name TEXT NOT NULL,
    counterparty_handle TEXT NOT NULL,
    to_currency TEXT,
    to_amount NUMERIC(24, 8),
    tx_hash TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS transactions_user_created_idx ON transactions(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('TRANSACTION', 'ACCOUNT', 'SECURITY')),
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    transaction_id UUID REFERENCES transactions(id) ON DELETE SET NULL,
    read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS notifications_user_created_idx ON notifications(user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS user_settings (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    biometric_approval BOOLEAN NOT NULL DEFAULT true,
    hide_balance BOOLEAN NOT NULL DEFAULT false,
    transaction_alerts BOOLEAN NOT NULL DEFAULT true,
    promotions BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS swap_quotes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    from_asset TEXT NOT NULL,
    to_asset TEXT NOT NULL,
    amount NUMERIC(24, 8) NOT NULL,
    fee NUMERIC(24, 8) NOT NULL,
    receive NUMERIC(24, 8) NOT NULL,
    rate NUMERIC(24, 12) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS idempotency_keys (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    response JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, operation, idempotency_key)
);

CREATE TABLE IF NOT EXISTS device_tokens (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fcm_token TEXT NOT NULL,
    platform TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, fcm_token)
);

CREATE TABLE IF NOT EXISTS demo_card_tokens (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    brand TEXT NOT NULL,
    last4 CHAR(4) NOT NULL,
    expiry TEXT NOT NULL,
    PRIMARY KEY (user_id, token)
);
