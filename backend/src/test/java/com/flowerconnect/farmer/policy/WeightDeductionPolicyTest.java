package com.flowerconnect.farmer.policy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit coverage for the pure weight-deduction policy (no database access).
 */
class WeightDeductionPolicyTest {

    @Test
    void deductionBoundaries() {
        assertEquals(new BigDecimal("0"), WeightDeductionPolicy.deductionKg(new BigDecimal("9.99")));
        assertEquals(new BigDecimal("1"), WeightDeductionPolicy.deductionKg(new BigDecimal("10")));
        assertEquals(new BigDecimal("1"), WeightDeductionPolicy.deductionKg(new BigDecimal("19.99")));
        assertEquals(new BigDecimal("2"), WeightDeductionPolicy.deductionKg(new BigDecimal("20")));
        assertEquals(new BigDecimal("2"), WeightDeductionPolicy.deductionKg(new BigDecimal("29.99")));
        assertEquals(new BigDecimal("3"), WeightDeductionPolicy.deductionKg(new BigDecimal("30")));
        assertEquals(new BigDecimal("3"), WeightDeductionPolicy.deductionKg(new BigDecimal("39.99")));
        assertEquals(new BigDecimal("4"), WeightDeductionPolicy.deductionKg(new BigDecimal("40")));
        assertEquals(new BigDecimal("4"), WeightDeductionPolicy.deductionKg(new BigDecimal("49.99")));
        assertEquals(new BigDecimal("5"), WeightDeductionPolicy.deductionKg(new BigDecimal("50")));
        assertEquals(new BigDecimal("5"), WeightDeductionPolicy.deductionKg(new BigDecimal("80")));
    }

    @Test
    void exampleFromPlan() {
        // gross 32 kg and rate 100/kg -> deduction 3 kg, net 29 kg, amount 2900
        assertEquals(new BigDecimal("3"), WeightDeductionPolicy.deductionKg(new BigDecimal("32")));
        assertEquals(new BigDecimal("29"),
                new BigDecimal("32").subtract(new BigDecimal("3")));
        assertEquals(new BigDecimal("2900"),
                new BigDecimal("29").multiply(new BigDecimal("100")));
    }

    @Test
    void negativeGrossKgRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightDeductionPolicy.deductionKg(new BigDecimal("-1")));
    }

    @Test
    void nullGrossKgRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WeightDeductionPolicy.deductionKg(null));
    }
}
