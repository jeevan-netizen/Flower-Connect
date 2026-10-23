package com.flowerconnect.payment.repository;

import com.flowerconnect.payment.domain.PaymentEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence for {@link PaymentEvent} rows (plan Phase 7, task 7.2).
 *
 * <p>Append-only log of provider events. {@code provider_event_id} carries a
 * database unique constraint for idempotency; the repository is intentionally
 * minimal because Stage 1 has no webhook or simulator to write events.
 */
@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    List<PaymentEvent> findByPaymentIdOrderByReceivedAtAsc(Long paymentId);

    boolean existsByProviderEventId(String providerEventId);
}
