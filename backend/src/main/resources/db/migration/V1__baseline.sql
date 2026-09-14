-- FlowerConnect V1__baseline.sql
-- Baseline schema for Phase 0: users and roles only.
-- All subsequent migrations are additive (no destructive changes without review).

CREATE TABLE roles (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(64)  NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE users (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    role_id       BIGINT         NOT NULL,
    email         VARCHAR(255)   NOT NULL,
    password_hash VARCHAR(255)   NOT NULL,
    full_name     VARCHAR(128)   NOT NULL,
    phone         VARCHAR(32)    NULL,
    is_active     TINYINT(1)     NOT NULL DEFAULT 1,
    created_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_users_role ON users (role_id);
CREATE INDEX idx_users_active ON users (is_active);