
-- Schema inicial do EventBook.
CREATE TABLE users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('COMPRADOR', 'ORGANIZADOR'))
);

CREATE TABLE events (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_events_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED', 'FINISHED')),
    CONSTRAINT chk_events_dates CHECK (ends_at > starts_at)
);

CREATE INDEX idx_events_organizer ON events(organizer_id);

CREATE TABLE ticket_lots (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    name VARCHAR(255) NOT NULL,
    unit_price NUMERIC(10, 2) NOT NULL,
    capacity INTEGER NOT NULL,
    sold INTEGER NOT NULL DEFAULT 0,
    reserved INTEGER NOT NULL DEFAULT 0,
    sales_start TIMESTAMPTZ NOT NULL,
    sales_end TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ticket_lots_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_ticket_lots_price CHECK (unit_price >= 0),
    CONSTRAINT chk_ticket_lots_capacity CHECK (capacity > 0),
    CONSTRAINT chk_ticket_lots_sold CHECK (sold >= 0),
    CONSTRAINT chk_ticket_lots_reserved CHECK (reserved >= 0),
    CONSTRAINT chk_ticket_lots_dates CHECK (sales_end > sales_start),
    CONSTRAINT chk_ticket_lots_capacity_limit CHECK (sold + reserved <= capacity)
);

CREATE INDEX idx_ticket_lots_event ON ticket_lots(event_id);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    buyer_id UUID NOT NULL REFERENCES users(id),
    ticket_lot_id UUID NOT NULL REFERENCES ticket_lots(id),
    quantity INTEGER NOT NULL,
    unit_price_snapshot NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_orders_quantity CHECK (quantity > 0),
    CONSTRAINT chk_orders_price CHECK (unit_price_snapshot >= 0)
);

CREATE INDEX idx_orders_buyer ON orders(buyer_id);
CREATE INDEX idx_orders_lot ON orders(ticket_lot_id);


CREATE INDEX idx_orders_pending_expiration ON orders(status, expires_at) WHERE status = 'PENDING';

CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    unique_code VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_tickets_unique_code UNIQUE (unique_code),
    CONSTRAINT chk_tickets_status CHECK (status IN ('VALID', 'CHECKED_IN', 'CANCELLED'))
);

CREATE INDEX idx_tickets_order ON tickets(order_id);

CREATE TABLE checkins (
    id UUID PRIMARY KEY,
    ticket_id UUID NOT NULL REFERENCES tickets(id),
    staff_id UUID NOT NULL REFERENCES users(id),
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_checkins_ticket UNIQUE (ticket_id)
);

CREATE INDEX idx_checkins_staff ON checkins(staff_id);

CREATE TABLE event_staff (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_event_staff_event_user UNIQUE (event_id, user_id)
);

CREATE INDEX idx_event_staff_user ON event_staff(user_id);