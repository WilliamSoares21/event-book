-- V9: Adiciona suporte ao status REFUNDED nas transações de pagamento
-- Mantém histórico append-only: não altera transações APPROVED originais

ALTER TABLE payment_transactions
DROP CONSTRAINT chk_payment_transactions_status,
ADD CONSTRAINT chk_payment_transactions_status CHECK (status IN ('APPROVED', 'DECLINED', 'REFUNDED'));

-- Impede estorno duplicado para o mesmo pedido (nível banco de dados)
CREATE UNIQUE INDEX uq_payment_transactions_refunded_order
    ON payment_transactions (order_id)
    WHERE status = 'REFUNDED';