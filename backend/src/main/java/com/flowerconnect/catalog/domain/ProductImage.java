package com.flowerconnect.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * An ordered image of a product (plan task 3.2).
 *
 * <p>Images live only in this table — never in a JSON column on
 * {@code products} — so they can be ordered, individually
 * addressed, and carry a primary flag. {@code sortOrder} defines
 * display order (0 = first); ties are broken by id.
 *
 * <p>At most one image per product may be primary. MySQL 8 has no
 * partial indexes, and a generated column that derives from a
 * foreign-key column cannot coexist with the foreign key (MySQL
 * error 1215), so the rule is a service-level transactional
 * invariant: the image service (plan task 3.8) clears the previous
 * primary in the same transaction that sets a new one.
 * {@code ProductImageRepository.countByProductIdAndPrimaryIsTrue}
 * is the read-side helper that check is built on. See
 * docs/decisions.md (D-21).
 *
 * <p>{@code storageKey} is the opaque key for the storage backend
 * (local path or S3 key); the actual bytes are handled by the
 * image pipeline (plan task 3.8), which is not part of this
 * stage.
 */
@Entity
@Table(name = "product_images")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "storage_key", length = 512, nullable = false)
    private String storageKey;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
