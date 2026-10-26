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

package com.flowerconnect.billing.controller;

import com.flowerconnect.billing.dto.VendorBillingRateResponse;
import com.flowerconnect.billing.service.CommissionRateService;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Vendor-facing read of the vendor's effective commission rate (percentage
 * 0-100). The effective rate combines the per-vendor override (if any) with the
 * platform_config global default.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/vendor/billing")
@RequiredArgsConstructor
public class VendorBillingRateController {

    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final CommissionRateService commissionRateService;

    /** Returns the caller's own effective rate (override wins, else platform default). */
    @GetMapping("/rate")
    public ResponseEntity<VendorBillingRateResponse> getRate(Authentication authentication) {
        User caller = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated user not found"));

        VendorProfile vendor = vendorProfileRepository.findByUserEmail(caller.getEmail())
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));

        BigDecimal effectivePercentage = commissionRateService.resolveEffectivePercentage(vendor);

        return ResponseEntity.ok(VendorBillingRateResponse.builder()
                .rate(effectivePercentage)
                .build());
    }
}
