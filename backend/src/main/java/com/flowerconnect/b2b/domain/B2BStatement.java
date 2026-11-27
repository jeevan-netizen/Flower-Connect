package com.flowerconnect.b2b.domain;

import com.flowerconnect.domain.VendorProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A B2B statement issued by a seller to a buyer for delivered, unpaid orders
 * (Stage 3 of the B2B module).
 *
 * <p>A statement groups one or more {@link B2BOrder}s that are {@code
 * DELIVERED} with {@code paymentStatus UNPAID}, belong to the same seller and
 * buyer, and fall within an inclusive period. Each order may belong to at most
 * one statement (enforced by {@code uq_b2b_statement_orders_order}). A
 * statement starts {@code OPEN} and moves to {@code PAID} when the seller
 * records payment; at that point every linked order's
 * {@code payment_status} is also marked {@code PAID}.
 *
 * <p>Only full payments are supported; there is no partial-payment concept and
 * no integration with the settlement ledger or customer invoices.
 */
@Entity
@Table(name = "b2b_statements")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class B2BStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "statement_no", length = 64, nullable = false, unique = true)
    private String statementNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private VendorProfile seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private VendorProfile buyer;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode")
    private PaymentMode paymentMode;

    @Column(name = "payment_note", length = 500)
    private String paymentNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Lifecycle state of a statement. Mirrors the {@code b2b_statements.status} ENUM exactly. */
    public enum Status {
        OPEN, PAID
    }

    /** Payment mode used to settle a statement. Mirrors the {@code b2b_statements.payment_mode} ENUM exactly. */
    public enum PaymentMode {
        CASH, UPI, BANK_TRANSFER
    }
}
