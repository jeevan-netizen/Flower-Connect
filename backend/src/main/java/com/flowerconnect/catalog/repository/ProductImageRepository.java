package com.flowerconnect.catalog.repository;

import com.flowerconnect.catalog.domain.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Product image persistence (plan task 3.2; image pipeline in task 3.8).
 *
 * <p>The one-primary-per-product rule is a <b>service-level</b> invariant (D-21):
 * MySQL has no filtered unique index, and the generated-column workaround cannot
 * coexist with the {@code product_id} foreign key (MySQL error 1215). Every write
 * path that can change {@code is_primary} therefore goes through the image
 * service, which clears the previous primary inside the same transaction.
 *
 * <p>{@link #findByProductIdForUpdate} is what makes that rule safe when two
 * requests race. Without the lock, two concurrent "make this the primary"
 * requests each read "no primary other than mine", each write their own row, and
 * commit — leaving two primaries, which is exactly the state the invariant exists
 * to prevent. Locking the product's image rows serialises the read-decide-write
 * sequence, the same discipline D-24 applies to stock rows.
 */
@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId ORDER BY i.sortOrder ASC, i.id ASC")
    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    /**
     * The product's images, ordered for display and locked
     * {@code SELECT ... FOR UPDATE}. Callers must already have checked ownership;
     * a lock on another vendor's rows would let any florist stall that product's
     * image set by naming a foreign id (the reason D-24 puts the ownership check
     * first).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ProductImage i WHERE i.product.id = :productId ORDER BY i.sortOrder ASC, i.id ASC")
    List<ProductImage> findByProductIdForUpdate(Long productId);

    long countByProductIdAndPrimaryIsTrue(Long productId);

    long countByProductId(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);

    List<ProductImage> findByProductId(Long productId);
}
