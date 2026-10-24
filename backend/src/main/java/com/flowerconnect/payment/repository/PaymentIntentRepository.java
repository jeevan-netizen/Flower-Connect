package com.flowerconnect.payment.repository;

import com.flowerconnect.payment.domain.PaymentIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Persistence for {@link PaymentIntent} rows (Phase 7, task 7.4).
 *
 * <p>There is one row per payment group, so {@code findByPaymentGroupId} is
 * the primary access path. The intent is locked implicitly through the order
 * and payment rows it relates to; concurrent resume attempts serialize on the
 * order-row lock taken by the state machine.
 */
@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, Long> {

    Optional<PaymentIntent> findByPaymentGroupId(String paymentGroupId);

    Optional<PaymentIntent> findByProviderReference(String providerReference);
}
