-- FlowerConnect V9__product_images.sql
-- Phase 3b (plan v2.2 task 3.2): product images.
--
-- `product_images` stores ordered images for a product. One image is
-- marked as primary (the cover image). Vendors manage their own images;
-- admins can see all. Images are not stored in a JSON column on
-- `products`; they have their own table for ordering, primary flag,
-- and metadata.
--
-- Design notes:
--   * `product_id` FK to `products` with cascade delete so images are
--     removed when a product is deleted.
--   * `sort_order` defines display order (0 = first). Ties broken by id.
--   * `is_primary` marks the cover image. At most one primary per
--     product. MySQL 8 has no partial indexes, and a generated column
--     that derives from a foreign-key column cannot coexist with the
--     foreign key (MySQL error 1215), so the one-primary rule is a
--     service-level transactional invariant: the image service (plan
--     task 3.8) clears the previous primary in the same transaction
--     that sets a new one. `ProductImageRepository.
--     countByProductIdAndPrimaryIsTrue` is the read-side helper that
--     check is built on. See docs/decisions.md (D-21).
--   * `storage_key` is the opaque key for the storage backend (local
--     path or S3 key). The filename is stored for reference.
--   * `mime_type` and `file_size` are stored for validation and display.
--   * The plan says images live only in `product_images` — no JSON column.
--
-- This migration is additive: no existing table, column, constraint or
-- index is altered or dropped, and no existing row is touched.

CREATE TABLE product_images (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    product_id        BIGINT         NOT NULL,
    storage_key       VARCHAR(512)   NOT NULL,
    original_filename VARCHAR(255)   NULL,
    mime_type         VARCHAR(100)   NOT NULL,
    file_size         BIGINT         NOT NULL,
    sort_order        INT            NOT NULL DEFAULT 0,
    is_primary        BIT(1)         NOT NULL DEFAULT b'0',
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_product_images PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE,
    CONSTRAINT ck_product_images_is_primary CHECK (is_primary IN (b'0', b'1'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_product_images_product ON product_images (product_id);
CREATE INDEX idx_product_images_product_sort ON product_images (product_id, sort_order, id);
