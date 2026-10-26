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

package com.flowerconnect.billing.domain;

import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.domain.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A commission record is created exactly once when an order moves to
 * {@code DELIVERED} (unique on {@code order_id}). The rate is snapshotted per
 * order at delivery time as a percentage ({@code commission_rate}), retained
 * with exactly two decimal places.
 *
 * <p>Commission base is order subtotal only:
 * {@code commission_amount = subtotal x multiplier}, scale 2 {@code HALF_UP},
 * where the multiplier comes from the delivery-time {@code CommissionRateService}.
 * Delivery fee and platform fee are stored but not included in commission.
 */
@Entity
@Table(name = "commissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Commission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorProfile vendor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    /** Applied percentage (0.00-100.00) with 2 decimal places. */
    @Column(name = "commission_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal commissionRate;

    @Column(name = "subtotal", precision = 12, scale = 2, nullable = false)
    private BigDecimal subtotal;

    @Column(name = "delivery_fee", precision = 12, scale = 2, nullable = false)
    private BigDecimal deliveryFee;

    @Column(name = "platform_fee", precision = 12, scale = 2, nullable = false)
    private BigDecimal platformFee;

    @Column(name = "commission_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal commissionAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
