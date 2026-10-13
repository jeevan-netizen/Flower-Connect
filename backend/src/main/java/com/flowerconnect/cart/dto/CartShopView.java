package com.flowerconnect.cart.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Per-vendor summary for the cart view (plan task 5.2).
 *
 * <p>The cart is grouped by vendor so the customer can see at a glance how
 * much each shop contributes and whether every line in that shop's section is
 * still purchasable. The frontend renders this as the per-vendor subtotal block
 * described in §6.2 of the Stage 0 design.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartShopView {

    private Long vendorId;
    private String vendorName;
    private int itemCount;
    private BigDecimal subtotal;
    private boolean purchasable;
}
