package com.flowerconnect.checkout.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Per-shop breakdown in a checkout quote.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutShopQuote {

    /**
     * The vendor's profile id.
     */
    private Long vendorId;

    /**
     * The vendor's business name.
     */
    private String vendorName;

    /**
     * Line items for this shop.
     */
    private List<CheckoutItemQuote> items;

    /**
     * Sum of {@code priceSnapshot × quantity} for this shop's items.
     */
    private BigDecimal subtotal;

    /**
     * Delivery fee for this shop (base + per-km × distance, or 0 if
     * subtotal ≥ free_delivery_above).
     */
    private BigDecimal deliveryFee;

    /**
     * Platform fee for this shop (from config or request override).
     */
    private BigDecimal platformFee;

    /**
     * The vendor's minimum order amount.
     */
    private BigDecimal minOrderAmount;

    /**
     * Shop total: subtotal + deliveryFee + platformFee.
     */
    private BigDecimal total;

    /**
     * Distance from vendor to delivery address in kilometres.
     */
    private BigDecimal distanceKm;

    /**
     * Whether the address is within the vendor's delivery radius.
     */
    private boolean withinRadius;

    /**
     * Whether the shop meets the vendor's minimum order requirement.
     */
    private boolean minOrderMet;

    /**
     * Whether the selected slot is available and valid.
     */
    private boolean slotAvailable;

    /**
     * Non-blocking warnings (e.g., price drift).
     */
    private List<String> warnings;

    /**
     * Blocking errors for this shop. If non-empty, this shop cannot be
     * checked out until resolved.
     */
    private List<String> errors;
}