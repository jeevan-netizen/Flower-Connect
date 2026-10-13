package com.flowerconnect.cart.repository;

import com.flowerconnect.cart.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Cart item persistence (plan Phase 5).
 *
 * <p>The {@code uq_cart_item_cart_product} constraint enforces one line per
 * product per cart; the service does an upsert (increment quantity) rather
 * than inserting duplicates.
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByCreatedAtAsc(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    long countByCartId(Long cartId);

    /** Deletes all items for a cart at once (used on checkout clear). */
    void deleteAllByCartId(Long cartId);
}
