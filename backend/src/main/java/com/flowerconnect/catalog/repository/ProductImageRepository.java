package com.flowerconnect.catalog.repository;

import com.flowerconnect.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Product image persistence (plan task 3.2).
 *
 * <p>The one-primary-per-product rule is enforced by the database
 * (generated column + unique index in {@code V9__product_images.sql});
 * {@code countByProductIdAndPrimaryIsTrue} is a read-side helper for
 * the service and tests, not the authority.
 */
@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId ORDER BY i.sortOrder ASC, i.id ASC")
    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    long countByProductIdAndPrimaryIsTrue(Long productId);

    List<ProductImage> findByProductId(Long productId);
}
