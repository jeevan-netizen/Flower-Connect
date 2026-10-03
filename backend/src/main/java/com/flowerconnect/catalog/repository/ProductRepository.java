package com.flowerconnect.catalog.repository;

import com.flowerconnect.catalog.domain.Product;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Product persistence (plan task 3.2).
 *
 * <p>Slug uniqueness is enforced by the database constraint; the
 * existence checks here are a fast path for the service's slug
 * generator, not the authority — {@code ProductService} retries on
 * a constraint violation to survive the check-then-insert race.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Existence check that ignores the product's own slug, used when a rename regenerates the slug. */
    boolean existsBySlugAndIdNot(String slug, Long id);

    Page<Product> findByVendorId(Long vendorId, Pageable pageable);

    Page<Product> findByVendorIdAndStatus(Long vendorId, ProductStatus status, Pageable pageable);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);
}
