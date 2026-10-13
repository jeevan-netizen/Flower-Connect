package com.flowerconnect.cart.repository;

import com.flowerconnect.cart.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Cart persistence (plan Phase 5).
 *
 * <p>One cart per customer is enforced at the entity level by the
 * {@code uq_carts_user} constraint — the unique key lives on the
 * {@code user_id} column, so a customer can never have two carts.
 */
@Repository
public interface CartRepository extends JpaRepository<Cart, Long> {

    /** Resolves a customer's cart by user id, or empty if they have none yet. */
    Optional<Cart> findByUserId(Long userId);

    /** Resolves a customer's cart by email (for JWT-subject lookups). */
    @Query("SELECT c FROM Cart c WHERE c.user.email = :email")
    Optional<Cart> findByUserEmail(@Param("email") String email);

    /** Deletes all cart items for the given cart (used on checkout clear). */
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = :cartId")
    void deleteAllItemsByCartId(@Param("cartId") Long cartId);

    long countByUserId(Long userId);
}
