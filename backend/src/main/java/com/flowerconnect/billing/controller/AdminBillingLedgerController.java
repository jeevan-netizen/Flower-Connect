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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Admin controller for viewing all ledger entries, outstanding balances and statements.
 */
@Slf4j
@Validated
@Tag(name = "Admin billing ledger", description = "ADMIN-only: all-vendor ledger, outstanding balances, overdue vendors, statements")
@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
public class AdminBillingLedgerController {

    private final BillingLedgerService billingLedgerService;
    private final StatementService statementService;
    private final com.flowerconnect.billing.service.OverdueDuesService overdueDuesService;

    @GetMapping("/ledger")
    @Operation(summary = "List all ledger entries (admin)",
            description = "Returns one page of all-vendor settlement entries with optional vendorId, "
                    + "date-range and type filters. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    public ResponseEntity<SettlementEntryPageResponse> getAllLedger(
            @RequestParam(required = false) Long vendorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) SettlementEntryType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<SettlementEntry> page = billingLedgerService.getAllLedger(vendorId, from, to, type, pageable);
        return ResponseEntity.ok(SettlementEntryPageResponse.fromPage(page));
    }

    @GetMapping("/outstanding/{vendorId}")
    @Operation(summary = "Get a vendor's outstanding balance (admin)",
            description = "Returns a vendor's signed outstanding balance. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Outstanding balance"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<OutstandingBalanceResponse> getOutstandingBalance(@PathVariable Long vendorId) {
        BigDecimal balance = billingLedgerService.getOutstandingBalance(vendorId);
        return ResponseEntity.ok(OutstandingBalanceResponse.builder()
                .vendorId(vendorId)
                .outstandingBalance(balance)
                .build());
    }

    @GetMapping("/overdue")
    @Operation(summary = "List overdue vendors (admin)",
            description = "Returns vendors with an overdue negative outstanding balance (days overdue "
                    + "exceeds the configured threshold). Advisory only; no automatic suspension. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Overdue vendor list"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    public ResponseEntity<java.util.List<com.flowerconnect.billing.dto.OverdueVendorResponse>> getOverdueVendors() {
        return ResponseEntity.ok(overdueDuesService.getOverdueVendors());
    }

    @GetMapping("/statement")
    @Operation(summary = "Download a vendor statement CSV (admin)",
            description = "Generates and downloads a CSV account statement for a vendor over a date "
                    + "range. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSV statement"),
            @ApiResponse(responseCode = "400", description = "Invalid date range"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor not found")
    })
    public ResponseEntity<byte[]> getStatement(
            @RequestParam Long vendorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate periodEnd) {

        byte[] csv = statementService.generateVendorStatementCsv(vendorId, periodStart, periodEnd);
        String filename = "statement-vendor-" + vendorId + "-" + periodStart + "-" + periodEnd + ".csv";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .body(csv);
    }
}
