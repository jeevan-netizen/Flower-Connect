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

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/admin/billing")
@RequiredArgsConstructor
public class AdminBillingLedgerController {

    private final BillingLedgerService billingLedgerService;
    private final StatementService statementService;

    @GetMapping("/ledger")
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
    public ResponseEntity<OutstandingBalanceResponse> getOutstandingBalance(@PathVariable Long vendorId) {
        BigDecimal balance = billingLedgerService.getOutstandingBalance(vendorId);
        return ResponseEntity.ok(OutstandingBalanceResponse.builder()
                .vendorId(vendorId)
                .outstandingBalance(balance)
                .build());
    }

    @GetMapping("/statement")
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
