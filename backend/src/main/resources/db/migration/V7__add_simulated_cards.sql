CREATE TABLE simulated_cards (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    last_four_digits VARCHAR(4) NOT NULL,
    brand VARCHAR(20) NOT NULL,
    expiry_month INTEGER NOT NULL,
    expiry_year INTEGER NOT NULL,
    total_limit NUMERIC(12,2) NOT NULL,
    available_limit NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_simulated_cards_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT chk_simulated_cards_expiry_month CHECK (expiry_month BETWEEN 1 AND 12),
    CONSTRAINT chk_simulated_cards_total_limit CHECK (total_limit >= 0),
    CONSTRAINT chk_simulated_cards_available_limit CHECK (available_limit >= 0),
    CONSTRAINT chk_simulated_cards_limit_available CHECK (available_limit <= total_limit)
);

CREATE INDEX idx_simulated_cards_user ON simulated_cards(user_id);

-- Regra D2: no máximo um cartão com status ACTIVE por usuário.
CREATE UNIQUE INDEX uk_simulated_cards_active_user ON simulated_cards(user_id) WHERE status = 'ACTIVE';
