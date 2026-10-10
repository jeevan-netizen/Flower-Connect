package com.flowerconnect.storefront.dto;

import com.flowerconnect.vendor.dto.VendorHoursResponse;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Public vendor profile on a storefront (plan task 4.5).
 *
 * <p>Contains only the storefront-relevant fields, following the
 * {@code DiscoveryResponse} precedent. Deliberately omitted, because they
 * are not public data: the owner's email and every other user field, the
 * commission rate, the approval status, the exact latitude/longitude, the
 * street address lines, and the audit timestamps. {@code VendorProfileResponse}
 * is the owning vendor's read model and must not be reused here — it carries
 * all of the above.
 *
 * <p>The coordinates are already published by discovery and search, so the
 * service area is reported the same way: city, area and pincode from the
 * chosen {@code service_locations} row. Distance and an estimated fee are
 * not part of this contract — a storefront is browsed without a search
 * origin, and the vendor's own delivery radius is what it is.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontVendorResponse {

    private Long id;
    private String businessName;
    private String description;

    /** A vendor-supplied URL. Not a {@code StorageService} key — see known issue 020. */
    private String logoUrl;

    private String city;
    private String area;
    private String pincode;

    private BigDecimal deliveryRadiusKm;

    private BigDecimal minOrderAmount;
    private BigDecimal baseDeliveryFee;
    private BigDecimal perKmFee;
    private BigDecimal freeDeliveryAbove;
    private Integer prepTimeMinutes;
    private Integer slotDurationMinutes;
    private Integer maxOrdersPerSlot;

    /**
     * {@code false} while the vendor has paused ordering. The storefront stays
     * readable — a paused shop is a browsable page, not a missing one — and
     * the flag is what lets a client show a closed-shop state.
     */
    private Boolean acceptingOrders;

    private BigDecimal avgRating;
    private Integer reviewCount;

    private List<VendorHoursResponse> hours;
}
