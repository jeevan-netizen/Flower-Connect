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

import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.dto.SettlementDetailResponse;
import com.flowerconnect.billing.dto.SettlementEntryResponse;
import com.flowerconnect.billing.dto.SettlementPageResponse;
import com.flowerconnect.billing.dto.SettlementResponse;
import com.flowerconnect.billing.service.SettlementService;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Vendor controller for viewing own weekly settlements and attached entries.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/vendor/billing/settlements")
@RequiredArgsConstructor
public class VendorSettlementController {

    private final SettlementService settlementService;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<SettlementPageResponse> getVendorSettlements(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "periodEnd", direction = Sort.Direction.DESC) Pageable pageable) {

        User caller = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated user not found"));

        VendorProfile vendor = vendorProfileRepository.findByUserEmail(caller.getEmail())
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));

        Page<Settlement> page = settlementService.getVendorSettlements(vendor.getId(), pageable);
        return ResponseEntity.ok(SettlementPageResponse.fromPage(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettlementDetailResponse> getVendorSettlement(
            @PathVariable Long id,
            Authentication authentication) {

        User caller = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated user not found"));

        VendorProfile vendor = vendorProfileRepository.findByUserEmail(caller.getEmail())
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));

        Settlement settlement = settlementService.getSettlementById(id);
        if (!settlement.getVendor().getId().equals(vendor.getId())) {
            throw BusinessException.forbidden("Vendor does not own settlement " + id);
        }

        List<SettlementEntry> entries = settlementService.getSettlementEntries(id);

        return ResponseEntity.ok(SettlementDetailResponse.builder()
                .settlement(SettlementResponse.fromEntity(settlement))
                .entries(entries.stream().map(SettlementEntryResponse::fromEntity).toList())
                .build());
    }
}
