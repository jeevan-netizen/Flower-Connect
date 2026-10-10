package com.flowerconnect.customer.repository;

import com.flowerconnect.customer.domain.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Address persistence (plan task 4.1).
 *
 * <p>Every query is scoped by {@code user_id}: the caller's identity comes
 * from the JWT subject, so an address that belongs to another customer is
 * indistinguishable from one that does not exist — both are an empty
 * result, which the service renders as 404. No method here can reach
 * another customer's rows.
 *
 * <p>The listing order — default first, then {@code id} ascending — lives
 * in the query rather than in a caller-supplied {@link Pageable} so the
 * API's documented order cannot be overridden by a client sort parameter.
 * Pagination is applied on top of that fixed order.
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {

    /**
     * One page of the customer's addresses: the default address first,
     * then remaining addresses in ascending id order.
     */
    @Query("SELECT a FROM Address a WHERE a.user.id = :userId "
            + "ORDER BY a.defaultAddress DESC, a.id ASC")
    Page<Address> findByUserId(@Param("userId") Long userId, Pageable pageable);

    /** All of the customer's addresses in the same fixed order. */
    @Query("SELECT a FROM Address a WHERE a.user.id = :userId "
            + "ORDER BY a.defaultAddress DESC, a.id ASC")
    List<Address> findAllByUserId(@Param("userId") Long userId);

    /**
     * The customer's own address by id. Returns empty both when the id does
     * not exist and when it belongs to another user, so the two cases stay
     * indistinguishable to the caller (404 either way).
     */
    @Query("SELECT a FROM Address a WHERE a.id = :id AND a.user.id = :userId")
    Optional<Address> findByIdAndUserId(@Param("id") Long id,
                                        @Param("userId") Long userId);

    /**
     * The customer's oldest remaining address ({@code MIN(id)}). Used to
     * promote a new default when the current default is deleted.
     */
    Optional<Address> findFirstByUserIdOrderByIdAsc(Long userId);

    long countByUserId(Long userId);
}
