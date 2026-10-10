package com.flowerconnect.repository;

import com.flowerconnect.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository
        extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmailWithRole(String email);

    @Deprecated
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.email = :email")
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.id = :id")
    Optional<User> findByIdWithRole(Long id);

    boolean existsByRoleName(String roleName);

    /**
     * Resolves and locks the user row with {@code SELECT ... FOR UPDATE}
     * in a single statement, for the rest of the transaction. The
     * address book (plan task 4.1) takes this lock before touching any
     * of the customer's address rows: the user row is the single
     * serialization point for every default-address mutation, and it is
     * the only row that exists even when the customer has no addresses
     * yet — which is exactly the case an address-row lock cannot cover
     * (two simultaneous first-address creations would otherwise both see
     * "no default" and both set the flag).
     *
     * <p>The lock is deliberately the <em>first</em> read in the
     * transaction, not the last (the D-24 discipline is inverted here,
     * on purpose): a plain read before the lock would establish the
     * REPEATABLE READ snapshot, and every later plain read in the
     * transaction — including the one that decides whether a default
     * already exists — would then miss rows another transaction committed
     * in between. A locking read reads the latest committed data and
     * establishes no snapshot, so the snapshot is created under the lock
     * and sees everything committed before it. Inverting D-24 is safe
     * here because the locked row is always the caller's own, resolved
     * from the JWT subject: no caller can name another user's row, so
     * the foreign-row stall attack D-24 guards against cannot happen.
     *
     * <p>The query joins nothing: a {@code JOIN FETCH} of the role
     * would lock the shared {@code roles} row too and serialise every
     * customer's address writes against each other. The write path never
     * reads the role, so the lazy proxy is never touched.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
}
