package com.flowerconnect.cart.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model for the customer's cart (plan task 5.2).
 *
 * <p>This is a single resource: the caller's own cart, resolved from the JWT
 * subject. The response carries both a flat {@code items} list (each item
 * carrying its vendor info) and a {@code shops} summary grouped by vendor, so
 * the frontend can render either shape without a second request.
 *
 * <p>The subtotal is the sum of all item subtals ({@code priceSnapshot ×
 * quantity}); no delivery fee or platform fee is included here — those are
 * computed by the checkout quote (tasks 5.3/5.4).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartResponse {

    private Long cartId;
    private int totalItems;
    private BigDecimal subtotal;

    /** Per-vendor grouping summary. */
    private List<CartShopView> shops;

    /** Flat list of every line item, each carrying its vendor info. */
    private List<CartItemResponse> items;
}
