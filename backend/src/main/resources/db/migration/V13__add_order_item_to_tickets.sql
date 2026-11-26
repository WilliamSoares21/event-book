-- Adicionar referência ao order_item na tabela tickets para suportar pedidos agrupados

ALTER TABLE tickets ADD COLUMN order_item_id UUID REFERENCES order_items(id);

CREATE INDEX idx_tickets_order_item ON tickets(order_item_id);