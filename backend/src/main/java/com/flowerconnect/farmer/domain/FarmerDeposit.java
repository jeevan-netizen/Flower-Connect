package com.flowerconnect.farmer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A vendor-entered flower deposit on a farmer/vendor link.
 * The persisted gross weight, deduction, net weight, rate and amount make
 * each row a self-contained historical record that is unaffected by later
 * policy changes.
 *
 * <p>Ownership is enforced in the service layer: a florist may only record
 * deposits on links belonging to their own vendor profile.
 */
@Entity
@Table(name = "farmer_deposits")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FarmerDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "flower_type", length = 160, nullable = false)
    private String flowerType;

    @Column(name = "gross_kg", precision = 10, scale = 2, nullable = false)
    private BigDecimal grossKg;

    @Column(name = "deduction_kg", precision = 10, scale = 2, nullable = false)
    private BigDecimal deductionKg;

    @Column(name = "net_kg", precision = 10, scale = 2, nullable = false)
    private BigDecimal netKg;

    @Column(name = "price_per_kg", precision = 10, scale = 2, nullable = false)
    private BigDecimal pricePerKg;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Column(name = "deposited_at", nullable = false)
    private LocalDateTime depositedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
