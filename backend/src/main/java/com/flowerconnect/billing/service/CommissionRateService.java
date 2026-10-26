/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flowerconnect.billing.service;

import com.flowerconnect.config.CheckoutProperties;
import com.flowerconnect.config.PlatformConfig;
import com.flowerconnect.config.PlatformConfigRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Commission-rate resolution and validation (Option A).
 *
 * <p>Rates are stored and exposed as a <strong>percentage 0-100 with 2 dp</strong>
 * ({@code DECIMAL(5,2)}). Internally, for money calculation
 * ({@code commissionAmount = subtotal x rate}), this service exposes a
 * <strong>0-1 multiplier</strong> (percentage / 100).
 *
 * <p>Resolution order for an order's commission rate:
 * <ol>
 *   <li>{@code vendor_profiles.commission_rate} (per-vendor override),</li>
 *   <li>{@code platform_config} row {@code commission_rate_default} (global
 *       default, seeded {@code 10.00} = 10%),</li>
 *   <li>{@link CheckoutProperties#getDefaultCommissionRate()} (0.10) - the
 *       legacy fallback, kept for compatibility and unit-test determinism.</li>
 * </ol>
 *
 * <p>Validation: {@code 0.00 <= rate <= 100.00}, at most 2 decimal places;
 * multiplier is clamped to {@code 0 <= m <= 1}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionRateService {

    public static final BigDecimal PERCENTAGE_FACTOR = BigDecimal.valueOf(100);
    public static final int MAX_PERCENTAGE_SCALE = 2;
    public static final int MULTIPLIER_SCALE = 4;
    public static final BigDecimal MIN_MULTIPLIER = BigDecimal.ZERO;
    public static final BigDecimal MAX_MULTIPLIER = BigDecimal.ONE;

    public static final String COMMISSION_RATE_DEFAULT_KEY = "commission_rate_default";

    private final PlatformConfigRepository platformConfigRepository;
    private final CheckoutProperties checkoutProperties;

    // ------------------------------------------------------------------
    // Public resolution API
    // ------------------------------------------------------------------

    /**
     * Resolve the delivery-time commission rate for a vendor.
     *
     * <p>Returns a 0-1 multiplier. The configuration value is validated and the
     * multiplier is rounded to 4 dp before the final {@code x subtotal}
     * multiplication (which is then rounded HALF_UP to 2 dp).
     *
     * @param vendor the vendor whose rate is resolved
     * @return 0-1 multiplier
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveRate(VendorProfile vendor) {
        BigDecimal percentage = readVendorOverride(vendor);
        if (percentage == null) {
            percentage = readPlatformConfigDefault();
        }
        if (percentage == null) {
            // Legacy fallback: deployment-level default. Kept for unit-test
            // determinism (CheckoutProperties is mocked with 0.10).
            return checkoutProperties.getDefaultCommissionRate();
        }
        return validateAndToMultiplier(percentage);
    }

    /**
     * Resolves the effective commission rate as a percentage (0.00 to 100.00).
     *
     * @param vendor the vendor whose rate is resolved
     * @return percentage with 2 decimal places
     */
    @Transactional(readOnly = true)
    public BigDecimal resolveEffectivePercentage(VendorProfile vendor) {
        return toPercentage(resolveRate(vendor));
    }

    /** Stored percentage (0-100) represented by the vendor override, or {@code null}. */
    @Transactional(readOnly = true)
    public BigDecimal readVendorOverride(VendorProfile vendor) {
        if (vendor == null || vendor.getCommissionRate() == null) {
            return null;
        }
        validatePercentage(vendor.getCommissionRate());
        return vendor.getCommissionRate();
    }

    /** Stored percentage (0-100) read from the platform_config default row. */
    @Transactional(readOnly = true)
    public BigDecimal readPlatformConfigDefault() {
        PlatformConfig config = platformConfigRepository.findByConfigKey(COMMISSION_RATE_DEFAULT_KEY).orElse(null);
        if (config == null || config.getConfigValue() == null) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(config.getConfigValue().trim());
            validatePercentage(parsed);
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("Invalid commission_rate_default '{}' (non-numeric); falling through to CheckoutProperties", config.getConfigValue());
            return null;
        }
    }

    /**
     * Convert a stored percentage (0-100) into a 0-1 multiplier, validated first.
     * The multiplier is rounded to 4 dp internally before the
     * final commission multiplication.
     */
    public BigDecimal toMultiplier(BigDecimal percentage) {
        if (percentage == null) {
            return null;
        }
        return validateAndToMultiplier(percentage);
    }

    /**
     * Convert a 0-1 multiplier back into the stored percentage (0-100, 2 dp).
     * Used when re-exposing the rate so the value is a clean percentage for
     * consumers that do not want the internal multiplier.
     */
    public BigDecimal toPercentage(BigDecimal multiplier) {
        if (multiplier == null) {
            return null;
        }
        return multiplier.multiply(PERCENTAGE_FACTOR)
                .setScale(MAX_PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    /** Throw if {@code percentage} is out of range or has more than 2 dp. */
    public static void validatePercentage(BigDecimal percentage) {
        if (percentage == null) {
            return;
        }
        if (percentage.compareTo(BigDecimal.ZERO) < 0) {
            throw BusinessException.badRequest(
                    "Commission rate percentage must be >= 0.00 (got " + percentage + ")");
        }
        if (percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw BusinessException.badRequest(
                    "Commission rate percentage must be <= 100.00 (got " + percentage + ")");
        }
        if (percentage.scale() > MAX_PERCENTAGE_SCALE) {
            throw BusinessException.badRequest(
                    "Commission rate " + percentage + " has more than " + MAX_PERCENTAGE_SCALE
                            + " decimal places");
        }
    }

    /** Validate a 0-1 multiplier (0 <= m <= 1). Returns the clamped value. */
    public static BigDecimal validateMultiplier(BigDecimal multiplier) {
        if (multiplier == null) {
            throw BusinessException.badRequest("Commission multiplier must not be null");
        }
        if (multiplier.compareTo(MIN_MULTIPLIER) < 0) {
            throw BusinessException.badRequest(
                    "Commission multiplier must be >= 0 (got " + multiplier + ")");
        }
        if (multiplier.compareTo(MAX_MULTIPLIER) > 0) {
            throw BusinessException.badRequest(
                    "Commission multiplier must be <= 1 (got " + multiplier + ")");
        }
        return multiplier;
    }

    private BigDecimal validateAndToMultiplier(BigDecimal percentage) {
        validatePercentage(percentage);
        BigDecimal multiplier = percentage.divide(PERCENTAGE_FACTOR, MULTIPLIER_SCALE, RoundingMode.HALF_UP);
        return validateMultiplier(multiplier);
    }
}
