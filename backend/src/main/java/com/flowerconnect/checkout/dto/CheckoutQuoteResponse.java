package com.flowerconnect.checkout.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response payload for {@code POST /api/v1/checkout/quote}.
 *
 * <p>Returns a per-shop breakdown with subtotals, delivery fees, platform
 * fee, and the grand total. Each shop carries its own warnings (e.g.,
 * price drift) and validation results so the customer can fix one shop
 * without losing the others.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckoutQuoteResponse {

    /**
     * Per-vendor breakdown.
     */
    private List<CheckoutShopQuote> shops;

    /**
     * Grand total across all shops (sum of shop totals).
     */
    private BigDecimal total;

    /**
     * Whether the quote has any blocking errors. If true, checkout cannot
     * proceed until the customer resolves them.
     */
    private boolean hasErrors;

    /**
     * Whether the quote contains price drift warnings (current price differs
     * from the cart's stored price snapshot). If true, the customer must
     * explicitly confirm the updated totals before proceeding.
     */
    private boolean hasPriceChanges;
}