package com.flowerconnect.order.service;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.config.PlatformConfigRepository;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
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
import java.time.ZoneId;

/**
 * Auto-reject job for stale PLACED orders (plan Phase 6, task 6.5).
 *
 * <p>Unaccepted PLACED orders older than a configurable number of minutes are
 * auto-cancelled with the reservation released. The age threshold is read from
 * {@code platform_config} (`auto_reject_minutes` row, seeded at 60 minutes by
 * {@code V18}); if the row is missing the documented default is used and a
 * warning is logged, so auto-reject is never silently disabled.
 *
 * <p>Each candidate is processed in its own {@link Propagation#REQUIRES_NEW}
 * transaction, so one failing order never rolls back or blocks another. The
 * order row is locked and its status re-checked inside
 * {@link OrderStateMachine#transition}: a vendor who accepted the order first
 * leaves it CONFIRMED and the auto-reject is skipped; an already-cancelled
 * order is skipped without a duplicate history row or stock movement.
 *
 * <p>The scheduled cadence and batch bound come from {@code AppProperties}
 * ({@code app.auto-reject-cron}, {@code app.auto-reject-batch-size}); the age
 * threshold itself always comes from the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRejectService {

    /** Reason written on the single {@code order_status_history} row per auto-reject. */
    public static final String AUTO_REJECT_REASON = "AUTO_REJECTED_NO_RESPONSE";

    /** Config key for the auto-reject threshold in {@code platform_config}. */
    private static final String AUTO_REJECT_MINUTES_KEY = "auto_reject_minutes";

    /** Documented default if {@code platform_config} has no auto_reject_minutes row. */
    private static final int DEFAULT_AUTO_REJECT_MINUTES = 60;

    private final PlatformConfigRepository platformConfigRepository;
    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final AppProperties appProperties;
    private final Clock clock;

    // Self-injection so rejectOrderIfStale with REQUIRES_NEW is invoked through
    // the Spring proxy and not by direct self-invocation.
    private AutoRejectService self;

    @PostConstruct
    public void initSelf() {
        this.self = this;
    }

    /**
     * The auto-reject threshold in minutes. Read from {@code platform_config};
     * falls back to the documented default if the row is absent.
     */
    public int getAutoRejectMinutes() {
        return platformConfigRepository.findByConfigKey(AUTO_REJECT_MINUTES_KEY)
                .map(cfg -> parseMinutes(cfg.getConfigValue()))
                .orElseGet(() -> {
                    log.warn("platform_config row '{}' missing; using documented default {} minutes",
                            AUTO_REJECT_MINUTES_KEY, DEFAULT_AUTO_REJECT_MINUTES);
                    return DEFAULT_AUTO_REJECT_MINUTES;
                });
    }

    private static int parseMinutes(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "platform_config.auto_reject_minutes value '" + value + "' is not a valid integer", e);
        }
    }

    /**
     * Runs the auto-reject job once: selects PLACED orders strictly older than
     * the configured threshold and cancels each one through the state machine.
     */
    public void runAutoReject() {
        int minutes = getAutoRejectMinutes();
        Instant cutoffInstant = clock.instant().minusSeconds((long) minutes * 60);
        LocalDateTime cutoff = LocalDateTime.ofInstant(cutoffInstant, clock.getZone());

        Page<Order> candidates = orderRepository.findByStatusAndCreatedAtBefore(
                OrderStatus.PLACED, cutoff,
                PageRequest.of(0, appProperties.getAutoRejectBatchSize()));

        log.info("Auto-reject: {} candidate(s) older than {} minute(s), batch size {}",
                candidates.getTotalElements(), minutes, appProperties.getAutoRejectBatchSize());

        for (Order order : candidates.getContent()) {
            try {
                self.rejectOrderIfStale(order.getId());
                log.info("Auto-rejected order {} (PLACED for more than {} minute(s))",
                        order.getId(), minutes);
            } catch (BusinessException e) {
                if (e.getErrorCode() == ErrorCode.CONFLICT) {
                    // Vendor accepted, already cancelled/rejected, or otherwise
                    // not PLACED: state machine re-checked under the lock.
                    log.debug("Skipping auto-reject for order {}: {}", order.getId(), e.getMessage());
                } else {
                    log.error("Failed to auto-reject order {}: {}", order.getId(), e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("Failed to auto-reject order {}: {}", order.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Cancels one order if it is still PLACED. Runs in its own transaction so
     * a failure here never rolls back another candidate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejectOrderIfStale(Long orderId) {
        orderStateMachine.transition(orderId, OrderStatus.CANCELLED,
                ActorType.SYSTEM, null, AUTO_REJECT_REASON);
    }
}
