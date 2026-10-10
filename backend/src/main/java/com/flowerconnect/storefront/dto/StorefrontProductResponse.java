package com.flowerconnect.storefront.dto;

import lombok.*;

import java.math.BigDecimal;

/**
 * Public product row on a vendor storefront (plan task 4.5).
 *
 * <p>Availability is a single {@code inStock} flag rather than the raw
 * {@code quantity} / {@code reserved_quantity} pair: the storefront answers
 * "can this be bought now?", and neither number is a customer-facing
 * statement. The flag is derived from the availability the
 * {@code Inventory} entity itself computes ({@code quantity −
 * reservedQuantity > 0}), so it cannot drift from the rule every other
 * caller uses.
 *
 * <p>A row with {@code inStock = false} is still returned. The plan's
 * wording is "active products" — the lifecycle status, not the stock
 * level — and silently dropping an ACTIVE product because its stock ran
 * out would make a vendor's listing disappear from their own storefront
 * while the catalog screen still shows it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorefrontProductResponse {

    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String slug;
    private String description;
    private BigDecimal basePrice;

    /**
     * {@code true} when the product has units a customer can still buy
     * ({@code quantity − reservedQuantity > 0}). Named after the state, not
     * the question — see {@code docs/decisions.md} (D-33).
     */
    private boolean inStock;
}
