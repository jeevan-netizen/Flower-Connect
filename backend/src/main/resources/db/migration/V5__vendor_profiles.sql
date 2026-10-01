-- FlowerConnect V5__vendor_profiles.sql
-- Vendor profile data model: vendor_profiles (plan v2.2 tasks 2.2 and 2.3) and
-- vendor_hours (task 2.4).
-- Coordinates are copied from the chosen service_locations centroid; service
-- locations remain the only source of coordinates in the system (D-4).
-- No seed data: vendor profiles are created through vendor registration (2.5).

CREATE TABLE vendor_profiles (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    user_id               BIGINT        NOT NULL,
    business_name         VARCHAR(160)  NOT NULL,
    description           VARCHAR(1000) NULL,
    address_line1         VARCHAR(255)  NOT NULL,
    address_line2         VARCHAR(255)  NULL,
    service_location_id   BIGINT        NOT NULL,
    latitude              DECIMAL(10,8) NOT NULL,
    longitude             DECIMAL(11,8) NOT NULL,
    delivery_radius_km    DECIMAL(5,2)  NOT NULL DEFAULT 5.00,
    logo_url              VARCHAR(512)  NULL,
    status                ENUM('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SUSPENDED') NOT NULL DEFAULT 'PENDING_APPROVAL',
    commission_rate       DECIMAL(5,4)  NULL,
    avg_rating            DECIMAL(3,2)  NULL,
    review_count          INT           NOT NULL DEFAULT 0,
    min_order_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    base_delivery_fee     DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    per_km_fee            DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    free_delivery_above   DECIMAL(10,2) NULL,
    prep_time_minutes     INT           NOT NULL DEFAULT 30,
    slot_duration_minutes INT           NOT NULL DEFAULT 60,
    max_orders_per_slot   INT           NOT NULL DEFAULT 10,
    accepting_orders      BIT(1)        NOT NULL DEFAULT b'1',
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_vendor_profiles PRIMARY KEY (id),
    CONSTRAINT uq_vendor_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_vendor_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_vendor_profiles_location FOREIGN KEY (service_location_id) REFERENCES service_locations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_vendor_profiles_status_geo ON vendor_profiles (status, latitude, longitude);

CREATE TABLE vendor_hours (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    vendor_profile_id BIGINT      NOT NULL,
    weekday           ENUM('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY') NOT NULL,
    open_time         TIME        NULL,
    close_time        TIME        NULL,
    closed            BIT(1)      NOT NULL DEFAULT b'0',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_vendor_hours PRIMARY KEY (id),
    CONSTRAINT uq_vendor_hours_profile_weekday UNIQUE (vendor_profile_id, weekday),
    CONSTRAINT fk_vendor_hours_profile FOREIGN KEY (vendor_profile_id) REFERENCES vendor_profiles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
