-- FlowerConnect V35__create_farmer_vendor_links.sql
-- Stage 1 of the farmer module: farmer/vendor link requests and vendor decisions.
-- Additive migration: no existing column, constraint or index is altered.

-- Seed the FARMER role (id 4), following the idempotent V3 seeding pattern.
INSERT IGNORE INTO roles (id, name) VALUES (4, 'FARMER');

CREATE TABLE farmer_vendor_links (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    farmer_id     BIGINT       NOT NULL,
    vendor_id     BIGINT       NOT NULL,
    status        ENUM('PENDING','ACTIVE','REJECTED','SUSPENDED') NOT NULL DEFAULT 'PENDING',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_farmer_vendor_links PRIMARY KEY (id),
    CONSTRAINT uq_farmer_vendor_links UNIQUE (farmer_id, vendor_id),
    CONSTRAINT fk_farmer_vendor_links_farmer FOREIGN KEY (farmer_id) REFERENCES users (id),
    CONSTRAINT fk_farmer_vendor_links_vendor FOREIGN KEY (vendor_id) REFERENCES vendor_profiles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_farmer_vendor_links_vendor_status ON farmer_vendor_links (vendor_id, status);
CREATE INDEX idx_farmer_vendor_links_farmer_status ON farmer_vendor_links (farmer_id, status);
