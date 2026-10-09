package com.flowerconnect.customer.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read model for an address (plan task 4.1).
 *
 * <p>The service location is flattened to its id plus the city, area and
 * pincode, so the client can render where the address is without a second
 * request — the same convention as {@code ProductResponse}, which carries
 * the category name alongside the category id. The coordinates are the
 * server-derived centroid copy, never a client-supplied value.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressResponse {

    private Long id;
    private String label;
    private String line1;
    private String line2;
    private Long serviceLocationId;
    private String city;
    private String area;
    private String pincode;
    private BigDecimal latitude;
    private BigDecimal longitude;

    /**
     * Whether this address is the customer's default. The property is
     * named {@code defaultAddress} (as on the entity) rather than
     * {@code isDefault}: Lombok generates an {@code isDefault()} getter
     * for a primitive boolean field whose name starts with "is", and
     * Jackson strips that "is" prefix, so the wire name would silently
     * become {@code default} instead of {@code isDefault}. The same
     * reasoning the entity documents for its own property applies here.
     */
    private boolean defaultAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
