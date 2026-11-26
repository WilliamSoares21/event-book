-- Migração para suportar pedidos agrupados (múltiplos lotes por pedido)
-- Cria tabela order_items e migra dados existentes

-- 1. Criar tabela order_items
CREATE TABLE order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    ticket_lot_id UUID NOT NULL REFERENCES ticket_lots(id),
    quantity INTEGER NOT NULL,
    unit_price_snapshot NUMERIC(10, 2) NOT NULL,
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price CHECK (unit_price_snapshot >= 0)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_lot ON order_items(ticket_lot_id);

-- 2. Migrar dados existentes: cada order vira 1 order_item
INSERT INTO order_items (order_id, ticket_lot_id, quantity, unit_price_snapshot)
SELECT id, ticket_lot_id, quantity, unit_price_snapshot FROM orders;

-- 3. Adicionar coluna total_price_snapshot em orders (cache do total)
ALTER TABLE orders ADD COLUMN total_price_snapshot NUMERIC(10, 2) NOT NULL DEFAULT 0;

-- 4. Atualizar total_price_snapshot com base nos itens migrados
UPDATE orders o
SET total_price_snapshot = (
    SELECT COALESCE(SUM(oi.unit_price_snapshot * oi.quantity), 0)
    FROM order_items oi
    WHERE oi.order_id = o.id
);

-- 5. Remover colunas antigas de orders (após migração confirmada)
-- Nota: Mantemos as colunas por compatibilidade, mas marcamos como deprecated
-- ALTER TABLE orders DROP COLUMN ticket_lot_id; -- COMENTADO: não remover ainda
-- ALTER TABLE orders DROP COLUMN quantity; -- COMENTADO: não remover ainda
-- ALTER TABLE orders DROP COLUMN unit_price_snapshot; -- COMENTADO: não remover ainda

-- 6. Constraint para garantir que order tenha pelo menos 1 item (será validado na aplicação)
-- ALTER TABLE orders ADD CONSTRAINT chk_orders_has_items CHECK (
--     EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = orders.id)
-- );