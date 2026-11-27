package com.flowerconnect.farmer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A vendor-entered flower deposit or a farmer collection request on a
 * farmer/vendor link. {@code farmer_payments} holds both deposits
 * (@{link FarmerDeposit}) and collections; this entity represents the
 * collection side while deposits keep their own table.
 *
 * <p>One {@code PENDING} collection per link is enforced by the generated
 * column {@code is_pending} plus the unique index
 * {@code uq_one_pending_payment_per_link} (migration V37).
 *
 * <p>Ownership is enforced in the service layer: a florist may only act on
 * payments whose link belongs to their own vendor profile.
 */
@Entity
@Table(name = "farmer_payments")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FarmerPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private Mode mode;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    public enum Mode {
        CASH, UPI
    }

    public enum Status {
        PENDING, CONFIRMED, REJECTED
    }
}
