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
 *
 * <p>The {@code quoteHash} is a server-generated fingerprint of all
 * material quote inputs. The client must echo it back in the checkout
 * request to prove they are confirming the exact quote they received.
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

    /**
     * Server-generated fingerprint of the quote. Must be provided in the
     * checkout request to bind price confirmation to this exact quote.
     * Computed as a hash of: customer identity, address, cart items
     * (product IDs, quantities, price snapshots), vendor slot selections,
     * delivery fees, platform fee, and totals.
     */
    private String quoteHash;
}