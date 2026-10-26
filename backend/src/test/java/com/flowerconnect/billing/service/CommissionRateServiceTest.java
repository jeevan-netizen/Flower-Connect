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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionRateServiceTest {

    @Mock
    private PlatformConfigRepository platformConfigRepository;
    @Mock
    private CheckoutProperties checkoutProperties;

    private CommissionRateService commissionRateService;

    @BeforeEach
    void setUp() {
        commissionRateService = new CommissionRateService(platformConfigRepository, checkoutProperties);
    }

    @Test
    @DisplayName("Vendor override takes precedence over platform default and legacy fallback")
    void vendorOverrideWins() {
        VendorProfile vendor = VendorProfile.builder()
                .commissionRate(new BigDecimal("15.50"))
                .build();

        BigDecimal multiplier = commissionRateService.resolveRate(vendor);
        assertThat(multiplier).isEqualByComparingTo(new BigDecimal("0.1550"));

        BigDecimal percentage = commissionRateService.resolveEffectivePercentage(vendor);
        assertThat(percentage).isEqualByComparingTo(new BigDecimal("15.50"));
    }

    @Test
    @DisplayName("Platform default is used when vendor override is null")
    void platformDefaultWhenVendorNull() {
        VendorProfile vendor = VendorProfile.builder()
                .commissionRate(null)
                .build();

        when(platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY))
                .thenReturn(Optional.of(PlatformConfig.builder()
                        .configKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY)
                        .configValue("12.00")
                        .build()));

        BigDecimal multiplier = commissionRateService.resolveRate(vendor);
        assertThat(multiplier).isEqualByComparingTo(new BigDecimal("0.1200"));

        BigDecimal percentage = commissionRateService.resolveEffectivePercentage(vendor);
        assertThat(percentage).isEqualByComparingTo(new BigDecimal("12.00"));
    }

    @Test
    @DisplayName("Legacy fallback is used when platform config is absent")
    void legacyFallbackWhenPlatformConfigMissing() {
        VendorProfile vendor = VendorProfile.builder()
                .commissionRate(null)
                .build();

        when(platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY))
                .thenReturn(Optional.empty());
        when(checkoutProperties.getDefaultCommissionRate())
                .thenReturn(new BigDecimal("0.10"));

        BigDecimal multiplier = commissionRateService.resolveRate(vendor);
        assertThat(multiplier).isEqualByComparingTo(new BigDecimal("0.10"));

        BigDecimal percentage = commissionRateService.resolveEffectivePercentage(vendor);
        assertThat(percentage).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("Legacy fallback is used when platform config is non-numeric")
    void legacyFallbackWhenPlatformConfigNonNumeric() {
        VendorProfile vendor = VendorProfile.builder()
                .commissionRate(null)
                .build();

        when(platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY))
                .thenReturn(Optional.of(PlatformConfig.builder()
                        .configKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY)
                        .configValue("invalid-rate")
                        .build()));
        when(checkoutProperties.getDefaultCommissionRate())
                .thenReturn(new BigDecimal("0.10"));

        BigDecimal multiplier = commissionRateService.resolveRate(vendor);
        assertThat(multiplier).isEqualByComparingTo(new BigDecimal("0.10"));
    }

    @Test
    @DisplayName("0.00% is valid and resolves to 0.0000 multiplier")
    void zeroPercentIsValid() {
        VendorProfile vendor = VendorProfile.builder()
                .commissionRate(new BigDecimal("0.00"))
                .build();

        BigDecimal multiplier = commissionRateService.resolveRate(vendor);
        assertThat(multiplier).isEqualByComparingTo(new BigDecimal("0.0000"));

        BigDecimal percentage = commissionRateService.resolveEffectivePercentage(vendor);
        assertThat(percentage).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("100.00% is valid and resolves to 1.0000 multiplier")
    void hundredPercentIsValid() {
        VendorProfile vendor = VendorProfile.builder()
                .commissionRate(new BigDecimal("100.00"))
                .build();

        BigDecimal multiplier = commissionRateService.resolveRate(vendor);
        assertThat(multiplier).isEqualByComparingTo(new BigDecimal("1.0000"));

        BigDecimal percentage = commissionRateService.resolveEffectivePercentage(vendor);
        assertThat(percentage).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Negative rate percentage is rejected")
    void negativeRateRejected() {
        assertThatThrownBy(() -> CommissionRateService.validatePercentage(new BigDecimal("-0.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(">= 0.00");
    }

    @Test
    @DisplayName("Rate percentage over 100.00 is rejected")
    void overHundredRateRejected() {
        assertThatThrownBy(() -> CommissionRateService.validatePercentage(new BigDecimal("100.01")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("<= 100.00");
    }

    @Test
    @DisplayName("Rate percentage with more than 2 decimal places is rejected")
    void moreThanTwoDecimalPlacesRejected() {
        assertThatThrownBy(() -> CommissionRateService.validatePercentage(new BigDecimal("10.125")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("decimal places");
    }

    @Test
    @DisplayName("Conversion between percentage and multiplier preserves values")
    void conversionPreservesValues() {
        assertThat(commissionRateService.toMultiplier(new BigDecimal("7.50")))
                .isEqualByComparingTo(new BigDecimal("0.0750"));
        assertThat(commissionRateService.toMultiplier(new BigDecimal("0.00")))
                .isEqualByComparingTo(new BigDecimal("0.0000"));
        assertThat(commissionRateService.toMultiplier(new BigDecimal("100.00")))
                .isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(commissionRateService.toMultiplier(null)).isNull();

        assertThat(commissionRateService.toPercentage(new BigDecimal("0.0750")))
                .isEqualByComparingTo(new BigDecimal("7.50"));
        assertThat(commissionRateService.toPercentage(new BigDecimal("0.0000")))
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(commissionRateService.toPercentage(new BigDecimal("1.0000")))
                .isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(commissionRateService.toPercentage(null)).isNull();
    }
}
