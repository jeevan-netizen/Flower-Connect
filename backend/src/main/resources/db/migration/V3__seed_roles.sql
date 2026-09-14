-- FlowerConnect V3__seed_roles.sql
-- Seeds the three role records needed for authentication (Phase 1).
-- Idempotent: uses INSERT IGNORE so re-runs are safe.

INSERT IGNORE INTO roles (id, name) VALUES
    (1, 'CUSTOMER'),
    (2, 'FLORIST'),
    (3, 'ADMIN');
