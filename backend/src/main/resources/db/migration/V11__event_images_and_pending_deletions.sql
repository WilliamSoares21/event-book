-- Tabela de imagens de capa de eventos
CREATE TABLE event_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    object_key VARCHAR(500) NOT NULL,
    public_url VARCHAR(1000) NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_event_images_event UNIQUE (event_id)
);

CREATE INDEX idx_event_images_event ON event_images(event_id);

-- Tabela para deleções pendentes no R2 (reprocessamento assíncrono)
CREATE TABLE pending_r2_deletions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    object_key VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    retries INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX idx_pending_r2_deletions_created ON pending_r2_deletions(created_at);