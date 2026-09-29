-- FlowerConnect V4__service_locations.sql
-- Seeded service locations for the demo region (Bengaluru, Karnataka).
-- One centroid per service area; coordinates are area-level approximations.
-- No GPS / live device coordinates (D-4).

CREATE TABLE service_locations (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    city        VARCHAR(128)   NOT NULL,
    area        VARCHAR(128)   NOT NULL,
    pincode     VARCHAR(10)    NOT NULL,
    latitude    DECIMAL(10,8)  NOT NULL,
    longitude   DECIMAL(11,8)  NOT NULL,
    created_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_service_locations PRIMARY KEY (id),
    CONSTRAINT uq_service_locations_city_area UNIQUE (city, area)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_service_locations_city_area ON service_locations (city, area);
CREATE INDEX idx_service_locations_pincode ON service_locations (pincode);

-- Seed data for Bengaluru demo region
-- Coordinates are approximate area centroids from public mapping data (OpenStreetMap / Google Maps)
INSERT INTO service_locations (city, area, pincode, latitude, longitude) VALUES
    ('Bengaluru', 'Koramangala',       '560034', 12.93520000, 77.62450000),
    ('Bengaluru', 'Indiranagar',       '560038', 12.97840000, 77.64080000),
    ('Bengaluru', 'Whitefield',        '560066', 12.96980000, 77.75000000),
    ('Bengaluru', 'HSR Layout',        '560102', 12.91160000, 77.64710000),
    ('Bengaluru', 'Jayanagar',         '560041', 12.92320000, 77.58360000),
    ('Bengaluru', 'Malleshwaram',      '560003', 13.00560000, 77.57070000),
    ('Bengaluru', 'Electronic City',   '560100', 12.84560000, 77.66030000),
    ('Bengaluru', 'Marathahalli',      '560037', 12.95920000, 77.69740000);