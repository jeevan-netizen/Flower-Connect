package com.flowerconnect.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_locations")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ServiceLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city", length = 128, nullable = false)
    private String city;

    @Column(name = "area", length = 128, nullable = false)
    private String area;

    @Column(name = "pincode", length = 10, nullable = false)
    private String pincode;

    @Column(name = "latitude", precision = 10, scale = 8, nullable = false)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8, nullable = false)
    private BigDecimal longitude;

    /**
     * The IANA timezone of this service area (decision D-5), used to interpret
     * {@code VendorHours} open/close times in local time. The server runs in
     * UTC; slot math converts via {@code Clock.systemUTC().withZone(ZoneId)}.
     * Defaults to {@code Asia/Kolkata} on the seeded demo rows (set in
     * {@code V13__create_carts_orders.sql}).
     */
    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}