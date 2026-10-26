/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flowerconnect.billing.service;

import com.flowerconnect.billing.domain.Commission;
import com.flowerconnect.billing.repository.CommissionRepository;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Creates the {@code commissions} row exactly once, inside the same transaction
 * that moves an order to {@code DELIVERED}. The rate is snapshotted at delivery
 * time: {@link CommissionRateService#resolveRate} is evaluated now, so a rate
 * change between checkout and delivery is reflected in this order's commission.
 *
 * <p>Commission base is order subtotal only:
 * {@code commissionAmount = subtotal x multiplier}, scale 2 {@code HALF_UP}.
 * Delivery fee and platform fee are excluded from commission calculation.
 *
 * <p>Idempotent: a second call returns the existing row (pre-check plus the unique
 * {@code (order_id)} constraint backstop).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionService {

    private final CommissionRepository commissionRepository;
    private final OrderRepository orderRepository;
    private final CommissionRateService commissionRateService;

    /** Idempotent read/create of the commission row for a DELIVERED order. */
    @Transactional
    public Commission createOrGet(Order order) {
        if (order == null || order.getId() == null) {
            return null;
        }

        if (commissionRepository.existsByOrderId(order.getId())) {
            return commissionRepository.findByOrderId(order.getId()).orElse(null);
        }

        Order reloaded = orderRepository.findById(order.getId()).orElse(order);
        if (reloaded.getStatus() != OrderStatus.DELIVERED) {
            log.warn("Cannot create commission for order {} with status {}: only DELIVERED orders can be commissioned",
                    reloaded.getId(), reloaded.getStatus());
            return null;
        }

        try {
            // Delivery-time rate snapshot
            BigDecimal multiplier = commissionRateService.resolveRate(reloaded.getVendor());
            BigDecimal ratePercentage = commissionRateService.toPercentage(multiplier);

            BigDecimal commissionAmount = reloaded.getSubtotal()
                    .multiply(multiplier != null ? multiplier : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);

            Commission commission = Commission.builder()
                    .order(reloaded)
                    .vendor(reloaded.getVendor())
                    .customer(reloaded.getUser())
                    .commissionRate(ratePercentage != null ? ratePercentage : BigDecimal.ZERO)
                    .subtotal(reloaded.getSubtotal())
                    .deliveryFee(reloaded.getDeliveryFee() != null ? reloaded.getDeliveryFee() : BigDecimal.ZERO)
                    .platformFee(reloaded.getPlatformFee() != null ? reloaded.getPlatformFee() : BigDecimal.ZERO)
                    .commissionAmount(commissionAmount)
                    .build();

            Commission saved = commissionRepository.saveAndFlush(commission);
            log.info("Commission {} created for order {} (vendor {}%, amount {})",
                    saved.getId(), reloaded.getId(), saved.getCommissionRate(), saved.getCommissionAmount());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.warn("Commission for order {} was created concurrently; returning existing", order.getId());
            return commissionRepository.findByOrderId(order.getId()).orElse(null);
        }
    }

    /**
     * Backfill commission records for all already-delivered orders that lack one.
     * Idempotent: can be executed multiple times without duplicate creation.
     *
     * @return count of newly created commission records
     */
    @Transactional
    public int backfillDeliveredOrders() {
        List<Order> candidates = orderRepository.findDeliveredOrdersWithoutCommission();
        int createdCount = 0;
        for (Order order : candidates) {
            Commission commission = createOrGet(order);
            if (commission != null) {
                createdCount++;
            }
        }
        log.info("Commission backfill completed: created {} records for {} candidates", createdCount, candidates.size());
        return createdCount;
    }
}
