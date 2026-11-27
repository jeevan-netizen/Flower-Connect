package com.flowerconnect.farmer.policy;

import java.math.BigDecimal;

/**
 * Pure weight-deduction policy for farmer deposits.
 *
 * <p>Lower-bound inclusive brackets:
 * <ul>
 *   <li>below 10 kg &rarr; deduct 0 kg</li>
 *   <li>10 kg to below 20 kg &rarr; deduct 1 kg</li>
 *   <li>20 kg to below 30 kg &rarr; deduct 2 kg</li>
 *   <li>30 kg to below 40 kg &rarr; deduct 3 kg</li>
 *   <li>40 kg to below 50 kg &rarr; deduct 4 kg</li>
 *   <li>50 kg and above &rarr; deduct 5 kg</li>
 * </ul>
 *
 * <p>No database access; intended to be unit-tested in isolation.
 */
public final class WeightDeductionPolicy {

    private static final BigDecimal THRESHOLD_10 = new BigDecimal("10");
    private static final BigDecimal THRESHOLD_20 = new BigDecimal("20");
    private static final BigDecimal THRESHOLD_30 = new BigDecimal("30");
    private static final BigDecimal THRESHOLD_40 = new BigDecimal("40");
    private static final BigDecimal THRESHOLD_50 = new BigDecimal("50");

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = new BigDecimal("1");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal THREE = new BigDecimal("3");
    private static final BigDecimal FOUR = new BigDecimal("4");
    private static final BigDecimal FIVE = new BigDecimal("5");

    private WeightDeductionPolicy() {}

    /**
     * Returns the deduction in kg for the given gross weight.
     *
     * @param grossKg the gross weight in kilograms, must not be null or negative
     * @return the deduction in kilograms
     * @throws IllegalArgumentException if {@code grossKg} is null or negative
     */
    public static BigDecimal deductionKg(BigDecimal grossKg) {
        if (grossKg == null) {
            throw new IllegalArgumentException("grossKg must not be null");
        }
        if (grossKg.signum() < 0) {
            throw new IllegalArgumentException("grossKg must not be negative");
        }

        if (grossKg.compareTo(THRESHOLD_50) >= 0) {
            return FIVE;
        }
        if (grossKg.compareTo(THRESHOLD_40) >= 0) {
            return FOUR;
        }
        if (grossKg.compareTo(THRESHOLD_30) >= 0) {
            return THREE;
        }
        if (grossKg.compareTo(THRESHOLD_20) >= 0) {
            return TWO;
        }
        if (grossKg.compareTo(THRESHOLD_10) >= 0) {
            return ONE;
        }
        return ZERO;
    }
}
