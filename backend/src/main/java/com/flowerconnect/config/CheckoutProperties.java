package com.flowerconnect.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Checkout and delivery-slot configuration ({@code app.checkout.*}).
 *
 * <p>Phase 5 checkout configuration. These values are deployment-level
 * constants, not admin-managed settings — the platform has no runtime settings
 * API yet (that arrives with the {@code platform_config} table in Phase 10).
 *
 * <p>Overrideable via env vars:
 * {@code APP_CHECKOUT_PLATFORM_FEE}, {@code APP_CHECKOUT_DEFAULT_COMMISSION_RATE},
 * {@code APP_CHECKOUT_MIN_LEAD_TIME_MINUTES}, {@code APP_CHECKOUT_MAX_BOOKING_HORIZON_DAYS}.
 *
 * @see AppProperties for the broader {@code app.* / APP_*} convention
 */
@Configuration
@ConfigurationProperties(prefix = "app.checkout")
public class CheckoutProperties {

    /**
     * Flat platform fee added to every order total (decision B-2). Zero by
     * default: Phase 5 is COD-only with no payment gateway.
     */
    private BigDecimal platformFee = BigDecimal.ZERO;

    /**
     * Default commission rate applied when a vendor has not set their own
     * {@code commission_rate}. 0.10 = 10%.
     */
    private BigDecimal defaultCommissionRate = new BigDecimal("0.10");

    /**
     * Minimum minutes between "now" and the earliest bookable slot
     * (decision D-5). A florist needs time to assemble flowers.
     */
    private int minLeadTimeMinutes = 120;

    /**
     * Maximum days into the future a slot may be booked (decision D-5).
     * Flowers expire quickly; one week is the booking ceiling.
     */
    private int maxBookingHorizonDays = 7;

    public BigDecimal getPlatformFee() {
        return platformFee;
    }

    public void setPlatformFee(BigDecimal platformFee) {
        this.platformFee = platformFee;
    }

    public BigDecimal getDefaultCommissionRate() {
        return defaultCommissionRate;
    }

    public void setDefaultCommissionRate(BigDecimal defaultCommissionRate) {
        this.defaultCommissionRate = defaultCommissionRate;
    }

    public int getMinLeadTimeMinutes() {
        return minLeadTimeMinutes;
    }

    public void setMinLeadTimeMinutes(int minLeadTimeMinutes) {
        this.minLeadTimeMinutes = minLeadTimeMinutes;
    }

    public int getMaxBookingHorizonDays() {
        return maxBookingHorizonDays;
    }

    public void setMaxBookingHorizonDays(int maxBookingHorizonDays) {
        this.maxBookingHorizonDays = maxBookingHorizonDays;
    }
}
