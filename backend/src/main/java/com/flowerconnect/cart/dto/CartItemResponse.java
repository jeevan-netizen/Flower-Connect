package com.flowerconnect.cart.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read model for one line in the customer's cart (plan task 5.2).
 *
 * <p>A cart item is purchasable when every eligibility condition holds:
 * the product is {@code ACTIVE}, its vendor is {@code APPROVED}, the vendor is
 * accepting orders, and the current available stock covers the item's quantity.
 * When any condition fails the item is still shown (the snapshot and the
 * quantity are still valid information) but {@code purchasable} is false and
 * {@code notPurchasableReason} explains why.
 *
 * <p>The price snapshot is authoritative for the cart total until checkout
 * (decision C-1); {@code currentPrice} is shown alongside so the customer can
 * see drift before the quote re-validates it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItemResponse {

    private Long id;
    private Long productId;
    private String productName;
    private String productSlug;
    private Long vendorId;
    private String vendorName;

    private int quantity;
    private BigDecimal priceSnapshot;
    private String note;

    /** {@code priceSnapshot × quantity}. */
    private BigDecimal subtotal;

    /** The product's current {@code base_price}, for drift display. */
    private BigDecimal currentPrice;

    /** True when {@code currentPrice} differs from {@code priceSnapshot}. */
    private boolean priceChanged;

    /** How many units the vendor currently has available to promise. */
    private Integer available;

    /** False when any eligibility condition fails. */
    private boolean purchasable;

    /** Human-readable reason the item is not purchasable, null when it is. */
    private String notPurchasableReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
