package com.flowerconnect.checkout.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Per-item breakdown in a checkout quote.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutItemQuote {

    /**
     * The product id.
     */
    private Long productId;

    /**
     * The product name.
     */
    private String productName;

    /**
     * The product slug.
     */
    private String productSlug;

    /**
     * The quantity ordered.
     */
    private int quantity;

    /**
     * The price snapshot from the cart (authoritative for the order total).
     */
    private BigDecimal priceSnapshot;

    /**
     * The product's current base price (for drift display).
     */
    private BigDecimal currentPrice;

    /**
     * True when {@code currentPrice} differs from {@code priceSnapshot}.
     */
    private boolean priceChanged;

    /**
     * The cart item's optional note.
     */
    private String note;

    /**
     * Line subtotal: {@code priceSnapshot × quantity}.
     */
    private BigDecimal subtotal;

    /**
     * How many units the vendor currently has available.
     */
    private Integer available;

    /**
     * Whether this specific item is purchasable (active product, approved
     * vendor, accepting orders, sufficient stock).
     */
    private boolean purchasable;

    /**
     * Human-readable reason the item is not purchasable, null when it is.
     */
    private String notPurchasableReason;
}