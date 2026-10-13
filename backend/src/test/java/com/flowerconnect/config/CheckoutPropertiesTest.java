package com.flowerconnect.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the defaults for {@link CheckoutProperties} (decision B-2, D-5).
 */
class CheckoutPropertiesTest {

    @Test
    void shouldDefaultPlatformFeeToZero() {
        CheckoutProperties props = new CheckoutProperties();
        assertEquals(BigDecimal.ZERO, props.getPlatformFee());
    }

    @Test
    void shouldDefaultCommissionRateToTenPercent() {
        CheckoutProperties props = new CheckoutProperties();
        assertEquals(new BigDecimal("0.10"), props.getDefaultCommissionRate());
    }

    @Test
    void shouldDefaultMinLeadTimeMinutesTo120() {
        CheckoutProperties props = new CheckoutProperties();
        assertEquals(120, props.getMinLeadTimeMinutes());
    }

    @Test
    void shouldDefaultMaxBookingHorizonDaysTo7() {
        CheckoutProperties props = new CheckoutProperties();
        assertEquals(7, props.getMaxBookingHorizonDays());
    }

    @Test
    void shouldAllowPlatformFeeOverride() {
        CheckoutProperties props = new CheckoutProperties();
        props.setPlatformFee(new BigDecimal("15.00"));
        assertEquals(new BigDecimal("15.00"), props.getPlatformFee());
    }
}
