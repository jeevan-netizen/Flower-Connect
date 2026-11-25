package com.flowerconnect.farmer.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A link request between a farmer (FARMER role) and a vendor (vendor_profiles row).
 * One link per farmer/vendor pair, enforced by {@code uq_farmer_vendor_links}.
 *
 * <p>The lifecycle: a farmer requests a link ({@code PENDING}); a vendor accepts
 * ({@code ACTIVE}), rejects ({@code REJECTED}), suspends ({@code SUSPENDED}) or
 * reactivates ({@code ACTIVE}) it. Invalid transitions are rejected by the service
 * layer with a {@code BusinessException}.
 */
@Entity
@Table(name = "farmer_vendor_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"farmer_id", "vendor_id"}))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FarmerVendorLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "farmer_id", nullable = false)
    private Long farmerId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING, ACTIVE, REJECTED, SUSPENDED
    }
}
