package com.flowerconnect.order.service;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.repository.OrderRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Reservation expiry job for stale {@code PENDING_PAYMENT} orders (plan Phase 7,
 * task 7.5).
 *
 * <p>Online orders sit in {@code PENDING_PAYMENT} while the customer completes
 * payment through the simulated gateway (task 7.4). If the intent expires before
 * the customer pays, the order must be cancelled and its reserved stock
 * released, otherwise reservations can leak forever. This sweeper selects
 * {@code PENDING_PAYMENT} orders older than a configurable number of minutes
 * and cancels each one through {@link OrderStateMachine}, which writes one
 * {@code order_status_history} row per transition and releases the reservation
 * through {@code InventoryService.releaseReservedStock}.
 *
 * <p>Each candidate is processed in its own
 * {@link Propagation#REQUIRES_NEW} transaction, so one failing order never
 * rolls back or blocks another. The order row is locked and its status
 * re-checked inside {@link OrderStateMachine#transition}: a customer who
 * resumed the order first leaves it {@code PLACED} and the expiry is skipped;
 * an already-cancelled order is skipped without a duplicate history row or
 * stock movement.
 *
 * <p>The scheduled cadence, batch bound and age threshold come from
 * {@code AppProperties} ({@code app.payment-expiry-cron},
 * {@code app.payment-expiry-batch-size}, {@code app.payment-expiry-minutes}).
 * The cron is disabled in the test profile so the job cannot fire mid-test;
 * the service is driven directly with the injected {@link Clock} instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentExpiryService {

    /** Reason written on the single {@code order_status_history} row per expiry. */
    public static final String PAYMENT_EXPIRY_REASON = "PAYMENT_EXPIRED_NO_RESPONSE";

    /** Documented default threshold in minutes. */
    private static final int DEFAULT_PAYMENT_EXPIRY_MINUTES = 30;

    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final AppProperties appProperties;
    private final Clock clock;

    // Self-injection so expireOrderIfStale with REQUIRES_NEW is invoked through
    // the Spring proxy and not by direct self-invocation.
    private PaymentExpiryService self;

    @PostConstruct
    public void initSelf() {
        this.self = this;
    }

    /**
     * The payment-expiry threshold in minutes, from {@code AppProperties}.
     */
    public int getPaymentExpiryMinutes() {
        return appProperties.getPaymentExpiryMinutes();
    }

    /**
     * Runs the payment-expiry job once: selects {@code PENDING_PAYMENT} orders
     * whose {@code created_at} is strictly older than the configured threshold
     * and cancels each one through the state machine.
     */
    public void runPaymentExpiry() {
        int minutes = getPaymentExpiryMinutes();
        Instant cutoffInstant = clock.instant().minusSeconds((long) minutes * 60);
        LocalDateTime cutoff = LocalDateTime.ofInstant(cutoffInstant, clock.getZone());

        Page<Order> candidates = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PENDING_PAYMENT, cutoff,
                PageRequest.of(0, appProperties.getPaymentExpiryBatchSize()));

        log.info("Payment expiry: {} candidate(s) older than {} minute(s), batch size {}",
                candidates.getTotalElements(), minutes, appProperties.getPaymentExpiryBatchSize());

        for (Order order : candidates.getContent()) {
            try {
                self.expireOrderIfStale(order.getId());
                log.info("Expired order {} (PENDING_PAYMENT for more than {} minute(s))",
                        order.getId(), minutes);
            } catch (BusinessException e) {
                if (e.getErrorCode() == com.flowerconnect.exception.ErrorCode.CONFLICT) {
                    // Customer resumed, already cancelled/rejected, or otherwise
                    // not PENDING_PAYMENT: state machine re-checked under the lock.
                    log.debug("Skipping expiry for order {}: {}", order.getId(), e.getMessage());
                } else {
                    log.error("Failed to expire order {}: {}", order.getId(), e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("Failed to expire order {}: {}", order.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Cancels one order if it is still {@code PENDING_PAYMENT}. Runs in its own
     * transaction so a failure here never rolls back another candidate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOrderIfStale(Long orderId) {
        orderStateMachine.transition(orderId, OrderStatus.CANCELLED,
                ActorType.SYSTEM, null, PAYMENT_EXPIRY_REASON);
    }
}
