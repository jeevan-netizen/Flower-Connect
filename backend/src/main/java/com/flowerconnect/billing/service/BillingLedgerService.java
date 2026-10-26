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
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.dispute.domain.Dispute;
import com.flowerconnect.dispute.domain.DisputeStatus;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.domain.Order.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service managing the append-only settlement ledger.
 *
 * <p>Ledger entries are created:
 * <ul>
 *   <li>On order delivery (ONLINE_ORDER_CREDIT: +(S - C + D), COD_DUES: -(C + P)).</li>
 *   <li>On dispute resolution (DISPUTE_ADJUSTMENT: -refundAmount or negated credit).</li>
 * </ul>
 *
 * <p>All operations are idempotent via unique constraints on order_id and dispute_id.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BillingLedgerService {

    private final SettlementEntryRepository settlementEntryRepository;

    /**
     * Creates a ledger entry for a delivered order.
     * Must be called in the same transaction as order delivery and commission creation.
     *
     * @param order the delivered order
     * @param commission the corresponding commission record
     * @return the created or existing settlement entry, or null if order not DELIVERED
     */
    @Transactional
    public SettlementEntry recordOrderDelivery(Order order, Commission commission) {
        if (order == null || order.getId() == null || commission == null) {
            return null;
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            log.warn("Cannot create ledger entry for order {} with status {}: only DELIVERED orders reach ledger",
                    order.getId(), order.getStatus());
            return null;
        }

        if (settlementEntryRepository.existsByOrderId(order.getId())) {
            return settlementEntryRepository.findByOrderId(order.getId()).orElse(null);
        }

        BigDecimal subtotal = order.getSubtotal();
        BigDecimal commissionAmount = commission.getCommissionAmount();
        BigDecimal deliveryFee = order.getDeliveryFee() != null ? order.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal platformFee = order.getPlatformFee() != null ? order.getPlatformFee() : BigDecimal.ZERO;

        BigDecimal amount;
        SettlementEntryType type;
        String reference;

        if (order.getPaymentMethod() == Order.PaymentMethod.ONLINE) {
            type = SettlementEntryType.ONLINE_ORDER_CREDIT;
            // +(S - C + D)
            amount = subtotal.subtract(commissionAmount).add(deliveryFee).setScale(2, RoundingMode.HALF_UP);
            reference = "Online order #" + order.getId() + " credit";
        } else {
            type = SettlementEntryType.COD_DUES;
            // -(C + P)
            amount = commissionAmount.add(platformFee).negate().setScale(2, RoundingMode.HALF_UP);
            reference = "COD order #" + order.getId() + " dues";
        }

        try {
            SettlementEntry entry = SettlementEntry.builder()
                    .vendor(order.getVendor())
                    .order(order)
                    .type(type)
                    .amount(amount)
                    .reference(reference)
                    .build();

            SettlementEntry saved = settlementEntryRepository.saveAndFlush(entry);
            log.info("Recorded ledger entry {} (type {}, amount {}) for order {}",
                    saved.getId(), saved.getType(), saved.getAmount(), order.getId());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.warn("Ledger entry for order {} already created concurrently; returning existing", order.getId());
            return settlementEntryRepository.findByOrderId(order.getId()).orElse(null);
        }
    }

    /**
     * Creates a dispute adjustment ledger entry atomically inside the dispute resolution transaction.
     *
     * @param dispute the resolved dispute
     * @param newStatus resolution status (RESOLVED_REFUND, RESOLVED_PARTIAL, RESOLVED_REJECTED)
     * @param refundAmount refund amount granted to the customer
     * @return the created or existing settlement entry, or null if REJECTED
     */
    @Transactional
    public SettlementEntry recordDisputeAdjustment(Dispute dispute, DisputeStatus newStatus, BigDecimal refundAmount) {
        if (dispute == null || dispute.getId() == null || newStatus == DisputeStatus.RESOLVED_REJECTED) {
            return null;
        }

        if (settlementEntryRepository.existsByDisputeId(dispute.getId())) {
            return settlementEntryRepository.findByDisputeId(dispute.getId()).orElse(null);
        }

        Order order = dispute.getOrder();
        BigDecimal amount;

        if (newStatus == DisputeStatus.RESOLVED_REFUND) {
            // Full reversal of vendor credit: negated original ONLINE_ORDER_CREDIT of the order
            Optional<SettlementEntry> originalCredit = settlementEntryRepository.findByOrderId(order.getId());
            if (originalCredit.isPresent() && originalCredit.get().getType() == SettlementEntryType.ONLINE_ORDER_CREDIT) {
                amount = originalCredit.get().getAmount().negate().setScale(2, RoundingMode.HALF_UP);
            } else {
                amount = (refundAmount != null ? refundAmount : order.getTotal()).negate().setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            // RESOLVED_PARTIAL -> amount = -refundAmount
            amount = (refundAmount != null ? refundAmount : BigDecimal.ZERO).negate().setScale(2, RoundingMode.HALF_UP);
        }

        try {
            SettlementEntry entry = SettlementEntry.builder()
                    .vendor(order.getVendor())
                    .dispute(dispute)
                    .type(SettlementEntryType.DISPUTE_ADJUSTMENT)
                    .amount(amount)
                    .reference("Dispute #" + dispute.getId() + " adjustment (" + newStatus + ") for order #" + order.getId())
                    .build();

            SettlementEntry saved = settlementEntryRepository.saveAndFlush(entry);
            log.info("Recorded dispute adjustment {} (amount {}) for dispute {}",
                    saved.getId(), saved.getAmount(), dispute.getId());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            log.warn("Ledger entry for dispute {} already created concurrently; returning existing", dispute.getId());
            return settlementEntryRepository.findByDisputeId(dispute.getId()).orElse(null);
        }
    }

    /**
     * Returns the outstanding vendor balance:
     * Sum of signed amounts for unattached rows or rows attached to a PENDING settlement.
     * Rows attached to a SETTLED settlement are excluded.
     *
     * @param vendorId the vendor ID
     * @return signed balance (positive = platform owes vendor, negative = vendor owes platform)
     */
    @Transactional(readOnly = true)
    public BigDecimal getOutstandingBalance(Long vendorId) {
        if (vendorId == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal balance = settlementEntryRepository.calculateOutstandingBalance(vendorId);
        return balance != null ? balance.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Lists paginated ledger entries for a vendor.
     */
    @Transactional(readOnly = true)
    public Page<SettlementEntry> getVendorLedger(Long vendorId, LocalDateTime from, LocalDateTime to,
                                                 SettlementEntryType type, Pageable pageable) {
        Specification<SettlementEntry> spec = (root, query, cb) -> {
            var predicate = cb.equal(root.get("vendor").get("id"), vendorId);
            if (from != null && to != null) {
                predicate = cb.and(predicate, cb.between(root.get("createdAt"), from, to));
            } else if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            } else if (to != null) {
                predicate = cb.and(predicate, cb.lessThan(root.get("createdAt"), to));
            }
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), type));
            }
            return predicate;
        };
        return settlementEntryRepository.findAll(spec, pageable);
    }

    /**
     * Admin view: lists paginated ledger entries across all or filtered vendors.
     */
    @Transactional(readOnly = true)
    public Page<SettlementEntry> getAllLedger(Long vendorId, LocalDateTime from, LocalDateTime to,
                                              SettlementEntryType type, Pageable pageable) {
        Specification<SettlementEntry> spec = (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (vendorId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("vendor").get("id"), vendorId));
            }
            if (from != null && to != null) {
                predicate = cb.and(predicate, cb.between(root.get("createdAt"), from, to));
            } else if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            } else if (to != null) {
                predicate = cb.and(predicate, cb.lessThan(root.get("createdAt"), to));
            }
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), type));
            }
            return predicate;
        };
        return settlementEntryRepository.findAll(spec, pageable);
    }
}
