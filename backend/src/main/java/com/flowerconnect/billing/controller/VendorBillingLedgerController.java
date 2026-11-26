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

import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.dto.OutstandingBalanceResponse;
import com.flowerconnect.billing.dto.SettlementEntryPageResponse;
import com.flowerconnect.billing.service.BillingLedgerService;
import com.flowerconnect.billing.service.StatementService;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Vendor billing ledger controller (plan Phase 10, task 10.4).
 *
 * <p>Endpoints are FLORIST-only and scoped to the calling vendor.
 */
@Slf4j
@Validated
@Tag(name = "Vendor billing ledger", description = "FLORIST-only: view your own ledger, outstanding balance, statement")
@RestController
@RequestMapping("/api/v1/vendor/billing")
@RequiredArgsConstructor
public class VendorBillingLedgerController {

    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final BillingLedgerService billingLedgerService;
    private final StatementService statementService;

    @GetMapping("/ledger")
    @Operation(summary = "List the vendor's ledger entries",
            description = "Returns one page of the calling vendor's settlement entries "
                    + "(attached and unattached) with optional date-range and type filters. FLORIST-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account")
    })
    public ResponseEntity<SettlementEntryPageResponse> getLedger(
            Authentication authentication,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) SettlementEntryType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        User caller = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated user not found"));

        VendorProfile vendor = vendorProfileRepository.findByUserEmail(caller.getEmail())
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));

        Page<SettlementEntry> page = billingLedgerService.getVendorLedger(vendor.getId(), from, to, type, pageable);
        return ResponseEntity.ok(SettlementEntryPageResponse.fromPage(page));
    }

    @GetMapping("/outstanding")
    @Operation(summary = "Get the vendor's outstanding balance",
            description = "Returns the calling vendor's signed outstanding balance. FLORIST-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Outstanding balance"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<OutstandingBalanceResponse> getOutstandingBalance(Authentication authentication) {
        User caller = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated user not found"));

        VendorProfile vendor = vendorProfileRepository.findByUserEmail(caller.getEmail())
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));

        BigDecimal balance = billingLedgerService.getOutstandingBalance(vendor.getId());
        return ResponseEntity.ok(OutstandingBalanceResponse.builder()
                .vendorId(vendor.getId())
                .outstandingBalance(balance)
                .build());
    }

    @GetMapping("/statement")
    @Operation(summary = "Download the vendor's statement CSV",
            description = "Generates and downloads a CSV account statement for the calling vendor "
                    + "over a date range. FLORIST-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV statement"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<byte[]> getStatement(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate periodEnd) {

        User caller = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated user not found"));

        VendorProfile vendor = vendorProfileRepository.findByUserEmail(caller.getEmail())
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));

        byte[] csv = statementService.generateVendorStatementCsv(vendor.getId(), periodStart, periodEnd);
        String filename = "statement-vendor-" + vendor.getId() + "-" + periodStart + "-" + periodEnd + ".csv";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}
