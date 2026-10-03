package com.flowerconnect.catalog.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A catalog category. Hierarchical: a category may have a parent, and the
 * parent of a top-level category is {@code null}. The FK references the same
 * table, so the depth is unbounded.
 *
 * <p>Admin-managed (plan task 3.1). Vendors never write a category directly;
 * they assign products to one by id through the catalog API. The hierarchy is
 * therefore read-only from the vendor's point of view, and the four seed rows
 * (Roses, Bouquets, Arrangements, Occasions) are the only top-level
 * categories in v1.
 *
 * <p>Two invariants are deliberately <em>not</em> enforced by the database:
 * <ul>
 *   <li><b>Cycle prevention</b> — a category may not become its own ancestor.
 *       A CHECK constraint cannot walk a chain, so the rule lives in
 *       {@code CategoryService} and is asserted before every write.</li>
 *   <li><b>Slug uniqueness</b> — the unique constraint on {@code slug} backs
 *       the service rule; the service generates the slug and is the only
 *       writer, so the two cannot drift.</li>
 * </ul>
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "slug", length = 128, nullable = false)
    private String slug;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}