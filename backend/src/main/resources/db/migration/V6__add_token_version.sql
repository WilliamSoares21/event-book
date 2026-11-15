-- Adiciona a coluna token_version para invalidação de logout
ALTER TABLE users
ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;