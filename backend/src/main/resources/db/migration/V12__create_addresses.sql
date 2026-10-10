-- FlowerConnect V12__create_addresses.sql
-- Customer address book (plan task 4.1). One row per saved address; a
-- customer may have many addresses, at most one of them the default.
-- Coordinates are copied from the chosen service_locations centroid at
-- write time and recopied whenever the service location changes (D-4);
-- service locations remain the only source of coordinates in the system.
-- No unique constraint on is_default: MySQL has no partial unique index,
-- and the generated-column trick cannot coexist with the required user_id
-- foreign key (MySQL error 1215 — the D-21 finding). The one-default-per-
-- customer rule is therefore a service-level invariant, enforced
-- transactionally by AddressService under a pessimistic lock on the owning
-- user row. No seed data: addresses are created through the address-book API.

CREATE TABLE addresses (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id               BIGINT        NOT NULL,
    label                 VARCHAR(128)  NOT NULL,
    line1                 VARCHAR(255)  NOT NULL,
    line2                 VARCHAR(255)  NULL,
    service_location_id   BIGINT        NOT NULL,
    latitude              DECIMAL(10,8) NOT NULL,
    longitude             DECIMAL(11,8) NOT NULL,
    is_default            BIT(1)        NOT NULL DEFAULT b'0',
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_addresses PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_addresses_service_location FOREIGN KEY (service_location_id) REFERENCES service_locations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_addresses_user ON addresses (user_id);
