package com.flowerconnect.pos.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Server-side POS money arithmetic (plan Phase 11, task 11.1).
 *
 * <p>Line totals and transaction totals are computed here with {@code
 * HALF_UP} rounding to 2 decimals, on {@code DECIMAL(10,2)} money. This is
 * the single place the POS total is derived, so the sale response, the
 * receipt and the daily summary all agree.
 */
public final class PosMoney {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PosMoney() {
    }

    /**
     * Round a monetary amount to 2 decimals, half up. Never uses {@code
     * double}.
     */
    public static BigDecimal round(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return amount.setScale(SCALE, ROUNDING);
    }

    /**
     * Line total for one POS line: {@code unitPrice * quantity}, rounded
     * half up to 2 decimals.
     */
    public static BigDecimal lineTotal(BigDecimal unitPrice, int quantity) {
        return round(unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }

    /**
     * Transaction total: the sum of rounded line totals, rounded half up to
     * 2 decimals (a no-op when every line is already rounded, but kept for
     * exactness).
     */
    public static BigDecimal transactionTotal(List<BigDecimal> lineTotals) {
        BigDecimal sum = lineTotals.stream()
                .map(PosMoney::round)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return round(sum);
    }
}
