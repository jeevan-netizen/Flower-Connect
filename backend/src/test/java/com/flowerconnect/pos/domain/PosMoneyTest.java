package com.flowerconnect.pos.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PosMoney}: HALF_UP rounding, line totals and
 * transaction totals (plan Phase 11, task 11.1).
 */
class PosMoneyTest {

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertThat(PosMoney.round(new BigDecimal("10.005"))).isEqualByComparingTo("10.01");
        assertThat(PosMoney.round(new BigDecimal("10.004"))).isEqualByComparingTo("10.00");
        assertThat(PosMoney.round(new BigDecimal("10.0051"))).isEqualByComparingTo("10.01");
        assertThat(PosMoney.round(new BigDecimal("9.995"))).isEqualByComparingTo("10.00");
        assertThat(PosMoney.round(BigDecimal.ZERO)).isEqualByComparingTo("0.00");
        assertThat(PosMoney.round(null)).isEqualByComparingTo("0.00");
    }

    @Test
    void lineTotalMultipliesUnitPriceByQuantity() {
        assertThat(PosMoney.lineTotal(new BigDecimal("12.50"), 3)).isEqualByComparingTo("37.50");
        assertThat(PosMoney.lineTotal(new BigDecimal("10.00"), 1)).isEqualByComparingTo("10.00");
        assertThat(PosMoney.lineTotal(new BigDecimal("0.33"), 3)).isEqualByComparingTo("0.99");
        assertThat(PosMoney.lineTotal(new BigDecimal("0.335"), 2)).isEqualByComparingTo("0.67");
    }

    @Test
    void transactionTotalSumsRoundedLineTotals() {
        BigDecimal total = PosMoney.transactionTotal(List.of(
                new BigDecimal("12.50"),
                new BigDecimal("37.50"),
                new BigDecimal("0.99")
        ));
        assertThat(total).isEqualByComparingTo("50.99");
    }

    @Test
    void transactionTotalRoundsTheSum() {
        BigDecimal total = PosMoney.transactionTotal(List.of(
                new BigDecimal("0.005"),
                new BigDecimal("0.005")
        ));
        // Each line rounds half up (0.01 + 0.01) then the sum is rounded.
        assertThat(total).isEqualByComparingTo("0.02");
    }

    @Test
    void neverUsesDouble() {
        BigDecimal amount = new BigDecimal("123456789.01");
        assertThat(PosMoney.round(amount)).isEqualByComparingTo("123456789.01");
    }
}
