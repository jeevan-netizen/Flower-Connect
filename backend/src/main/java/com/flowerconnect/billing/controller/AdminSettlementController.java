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
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.dto.FinalizeSettlementRequest;
import com.flowerconnect.billing.dto.RunSettlementRequest;
import com.flowerconnect.billing.dto.SettlementDetailResponse;
import com.flowerconnect.billing.dto.SettlementEntryResponse;
import com.flowerconnect.billing.dto.SettlementPageResponse;
import com.flowerconnect.billing.dto.SettlementResponse;
import com.flowerconnect.billing.service.SettlementService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin controller for managing weekly settlements, manual generation, and finalization.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/admin/billing/settlements")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementService settlementService;

    @PostMapping("/run")
    public ResponseEntity<List<SettlementResponse>> runSettlement(
            @Valid @RequestBody RunSettlementRequest request) {
        log.info("Admin triggered settlement run: vendorId={}, periodStart={}, periodEnd={}",
                request.getVendorId(), request.getPeriodStart(), request.getPeriodEnd());

        if (request.getVendorId() != null) {
            Settlement settlement = settlementService.runSettlement(
                    request.getVendorId(), request.getPeriodStart(), request.getPeriodEnd());
            return ResponseEntity.ok(List.of(SettlementResponse.fromEntity(settlement)));
        } else {
            List<Settlement> list = settlementService.runSettlementForAllVendors(
                    request.getPeriodStart(), request.getPeriodEnd());
            return ResponseEntity.ok(list.stream().map(SettlementResponse::fromEntity).toList());
        }
    }

    @GetMapping
    public ResponseEntity<SettlementPageResponse> listSettlements(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) SettlementStatus status,
            @PageableDefault(size = 20, sort = "periodEnd", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<Settlement> page = settlementService.listSettlements(vendorId, status, pageable);
        return ResponseEntity.ok(SettlementPageResponse.fromPage(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SettlementDetailResponse> getSettlement(@PathVariable Long id) {
        Settlement settlement = settlementService.getSettlementById(id);
        List<SettlementEntry> entries = settlementService.getSettlementEntries(id);

        return ResponseEntity.ok(SettlementDetailResponse.builder()
                .settlement(SettlementResponse.fromEntity(settlement))
                .entries(entries.stream().map(SettlementEntryResponse::fromEntity).toList())
                .build());
    }

    @PatchMapping("/{id}/finalize")
    public ResponseEntity<SettlementResponse> finalizeSettlement(
            @PathVariable Long id,
            @Valid @RequestBody FinalizeSettlementRequest request,
            Authentication authentication) {

        log.info("Admin {} finalizing settlement {}", authentication.getName(), id);
        Settlement settlement = settlementService.finalizeSettlement(
                id, authentication.getName(), request.getAdminReferenceNote());
        return ResponseEntity.ok(SettlementResponse.fromEntity(settlement));
    }
}
