-- FlowerConnect V2__add_refresh_tokens.sql
-- Adds the refresh_tokens table for JWT refresh-token rotation (Phase 1).

CREATE TABLE refresh_tokens (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    user_id    BIGINT         NOT NULL,
    token_hash VARCHAR(255)   NOT NULL,
    expires_at DATETIME(6)    NOT NULL,
    revoked    TINYINT(1)     NOT NULL DEFAULT 0,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
CREATE INDEX idx_refresh_tokens_revoked ON refresh_tokens (revoked);
