package com.flowerconnect.pos.domain;

import com.flowerconnect.domain.VendorProfile;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One walk-in point-of-sale sale (plan Phase 11, task 11.1).
 *
 * <p>A POS sale is commission-free and sits outside the marketplace ledger:
 * it creates no order, order-item, payment, commission or settlement row.
 * Its items carry server-side name/price snapshots taken at sale time.
 */
@Entity
@Table(name = "pos_transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PosTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorProfile vendor;

    @Column(name = "idempotency_key", length = 64, nullable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 128, nullable = false)
    private String requestFingerprint;

    @Column(name = "total", precision = 10, scale = 2, nullable = false)
    private BigDecimal total;

    @Column(name = "payment_method", length = 16, nullable = false)
    private String paymentMethod;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PosItem> items = new ArrayList<>();
}
