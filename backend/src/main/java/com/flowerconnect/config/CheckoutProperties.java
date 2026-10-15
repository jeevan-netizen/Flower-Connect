package com.flowerconnect.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

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
 * {@code APP_CHECKOUT_MIN_LEAD_TIME_MINUTES}, {@code APP_CHECKOUT_MAX_BOOKING_HORIZON_DAYS},
 * {@code APP_CHECKOUT_QUOTE_SIGNING_SECRET}.
 *
 * @see AppProperties for the broader {@code app.* / APP_*} convention
 */
@Configuration
@ConfigurationProperties(prefix = "app.checkout")
public class CheckoutProperties {

    public static final String INSECURE_DEFAULT_SECRET = "dev-only-insecure-default-change-in-production-min-32-chars";

    @Autowired(required = false)
    private Environment environment;

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

    /**
     * Secret key used to HMAC-sign quote hashes. Must be at least 32 characters.
     * Overrideable via {@code APP_CHECKOUT_QUOTE_SIGNING_SECRET}.
     * A production deployment must set this to a strong random value.
     */
    private String quoteSigningSecret = "dev-only-insecure-default-change-in-production-min-32-chars";


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

    public String getQuoteSigningSecret() {
        return quoteSigningSecret;
    }

    public void setQuoteSigningSecret(String quoteSigningSecret) {
        this.quoteSigningSecret = quoteSigningSecret;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        validate(this.environment);
    }

    public void validate(Environment env) {
        if (quoteSigningSecret == null || quoteSigningSecret.trim().length() < 32) {
            throw new IllegalStateException("Quote signing secret must be at least 32 characters");
        }
        if (env != null && env.acceptsProfiles(Profiles.of("prod"))
                && INSECURE_DEFAULT_SECRET.equals(quoteSigningSecret)) {
            throw new IllegalStateException("Default quote signing secret cannot be used in production profile");
        }
    }
}
