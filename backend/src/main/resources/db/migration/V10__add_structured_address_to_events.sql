-- V10__add_structured_address_to_events.sql
-- Adiciona endereço estruturado de 8 campos à tabela events (ADR-002, ADR-003, ADR-004)

-- 1. Novos campos (todos nullable para compatibilidade com eventos legados)
ALTER TABLE events
    ADD COLUMN venue_name VARCHAR(255),
    ADD COLUMN street VARCHAR(255),
    ADD COLUMN number VARCHAR(20),
    ADD COLUMN complement VARCHAR(100),
    ADD COLUMN neighborhood VARCHAR(100),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN state CHAR(2),
    ADD COLUMN zip_code VARCHAR(9);

-- 2. NÃO popular city/state a partir do location legado (ADR-003)
--    Os campos permanecem NULL para eventos legados sem informação confiável.
--    Preenchimento manual pelo organizador quando houver edição de localização.
--    NENHUM UPDATE heurístico de backfill — decisão explícita ADR-003.

-- 3. Constraints de integridade
ALTER TABLE events
    ADD CONSTRAINT chk_events_state_format
    CHECK (state IS NULL OR state IN ('AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA','PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO'));

ALTER TABLE events
    ADD CONSTRAINT chk_events_zip_code_format
    CHECK (zip_code IS NULL OR zip_code ~ '^\d{5}-?\d{3}$');

-- 4. Índices parciais para filtros da Fase 1 (apenas eventos PUBLISHED)
CREATE INDEX idx_events_status_city ON events(status, city)
    WHERE status = 'PUBLISHED' AND city IS NOT NULL;

CREATE INDEX idx_events_status_state ON events(status, state)
    WHERE status = 'PUBLISHED' AND state IS NOT NULL;

CREATE INDEX idx_events_status_starts_at ON events(status, starts_at)
    WHERE status = 'PUBLISHED';

CREATE INDEX idx_events_status_city_starts_at ON events(status, city, starts_at)
    WHERE status = 'PUBLISHED' AND city IS NOT NULL;

CREATE INDEX idx_events_status_title ON events(status, title)
    WHERE status = 'PUBLISHED';

CREATE INDEX idx_events_status_created_at ON events(status, created_at)
    WHERE status = 'PUBLISHED';

-- 5. Comentários para documentação
COMMENT ON COLUMN events.venue_name IS 'Nome do estabelecimento/local (ex: Arena Castelão)';
COMMENT ON COLUMN events.street IS 'Logradouro (ex: Av. Alberto Craveiro)';
COMMENT ON COLUMN events.number IS 'Número do endereço (ex: 2901, S/N)';
COMMENT ON COLUMN events.complement IS 'Complemento (ex: Ap 101, Bloco B)';
COMMENT ON COLUMN events.neighborhood IS 'Bairro (ex: Castelão)';
COMMENT ON COLUMN events.city IS 'Cidade — obrigatório para novos eventos';
COMMENT ON COLUMN events.state IS 'UF 2 letras maiúsculas — obrigatório para novos eventos';
COMMENT ON COLUMN events.zip_code IS 'CEP formato 00000-000 ou 00000000';