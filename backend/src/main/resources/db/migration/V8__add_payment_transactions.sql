CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    card_id UUID NOT NULL REFERENCES simulated_cards(id),
    amount NUMERIC(12,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    decline_reason VARCHAR(100),
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_transactions_status CHECK (status IN ('APPROVED', 'DECLINED')),
    CONSTRAINT chk_payment_transactions_amount CHECK (amount >= 0)
);

CREATE INDEX idx_payment_transactions_order ON payment_transactions(order_id);
CREATE INDEX idx_payment_transactions_card ON payment_transactions(card_id);
