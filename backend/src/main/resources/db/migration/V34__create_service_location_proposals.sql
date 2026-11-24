-- FlowerConnect V34__create_service_location_proposals.sql
-- Phase 14 (vendor registration): shop address and new service-area proposals.
--
-- A vendor can register a shop whose service area is not yet a seeded
-- service_locations row. That area cannot become a customer-selectable
-- delivery location straight away: a usable ServiceLocation needs valid city,
-- area, pincode, coordinates and a timezone, and the registration form cannot
-- supply the last two (and must not invent them). So the proposal is recorded
-- here in PENDING state and reviewed by an administrator. Approval creates the
-- real service_locations row (with the administrator-supplied coordinates and
-- timezone), at which point the area appears in the customer "Set your delivery
-- location" picker (GET /api/v1/locations). Rejection records a reason.
--
-- PENDING proposals are intentionally never returned by GET /api/v1/locations,
-- so a proposed area is not publicly discoverable or deliverable until approved.
-- Additive migration: no existing column, constraint or index is altered.

CREATE TABLE service_location_proposals (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    city                  VARCHAR(128) NOT NULL,
    area                  VARCHAR(128) NOT NULL,
    pincode               VARCHAR(10)  NOT NULL,
    address_line1         VARCHAR(255) NOT NULL,
    address_line2         VARCHAR(255) NULL,
    status                ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    proposed_by_user_id   BIGINT       NULL,
    proposed_by_email     VARCHAR(255) NULL,
    rejection_reason      VARCHAR(500) NULL,
    created_at            DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at           DATETIME(6)  NULL,
    reviewed_by_user_id   BIGINT       NULL,
    CONSTRAINT pk_service_location_proposals PRIMARY KEY (id),
    CONSTRAINT fk_service_location_proposals_proposer FOREIGN KEY (proposed_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_service_location_proposals_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id),
    CONSTRAINT uq_service_location_proposals_pending UNIQUE (city, area, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_service_location_proposals_status ON service_location_proposals (status, created_at);
CREATE INDEX idx_service_location_proposals_city_area ON service_location_proposals (city, area);
