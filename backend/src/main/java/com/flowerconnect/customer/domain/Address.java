package com.flowerconnect.customer.domain;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A customer's saved delivery address (plan task 4.1).
 *
 * <p>An address belongs to exactly one user and references one seeded
 * {@link ServiceLocation}. {@code latitude}/{@code longitude} are
 * <em>copies</em> of that location's centroid, taken at write time and
 * recopied whenever the service location changes — the D-4 precedent
 * ({@code vendor_profiles} does the same), so an address can be read
 * without joining {@code service_locations}.
 *
 * <p>{@code is_default} is a plain column with no unique constraint: MySQL
 * has no partial/filtered unique index, and the generated-column trick
 * cannot coexist with the required {@code user_id} foreign key (MySQL error
 * 1215 — the D-21 finding). The one-default-per-customer rule is therefore
 * a <b>service-level invariant</b> (D-21 precedent), enforced
 * transactionally by {@code AddressService} under a pessimistic lock on the
 * owning user row.
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "label", length = 128, nullable = false)
    private String label;

    @Column(name = "line1", length = 255, nullable = false)
    private String line1;

    @Column(name = "line2", length = 255)
    private String line2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_location_id", nullable = false)
    private ServiceLocation serviceLocation;

    @Column(name = "latitude", precision = 10, scale = 8, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal longitude;

    /**
     * Whether this address is the customer's default. The property is
     * named {@code defaultAddress} (column {@code is_default}) rather
     * than {@code isDefault} so the JPQL property path and the derived
     * query methods are unambiguous — a boolean field named {@code isX}
     * makes Spring Data's property resolution strip the prefix.
     */
    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
