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

import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates downloadable statements with OWASP CSV formula injection protection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SettlementEntryRepository settlementEntryRepository;
    private final VendorProfileRepository vendorProfileRepository;

    /**
     * Generates a CSV statement for a vendor across the specified period.
     * Every cell is strictly quoted, internal quotes are escaped (" -> ""), and any cell
     * starting with dangerous formula characters (=, +, -, @, \t, \r) is prefixed with a single apostrophe.
     *
     * @param vendorId the vendor ID
     * @param periodStart inclusive start date
     * @param periodEnd inclusive end date
     * @return UTF-8 encoded CSV bytes
     */
    @Transactional(readOnly = true)
    public byte[] generateVendorStatementCsv(Long vendorId, LocalDate periodStart, LocalDate periodEnd) {
        if (vendorId == null) {
            throw BusinessException.badRequest("vendorId is required");
        }
        if (periodStart == null || periodEnd == null) {
            throw BusinessException.badRequest("periodStart and periodEnd are required");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw BusinessException.badRequest("periodStart must be on or before periodEnd");
        }

        VendorProfile vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found: " + vendorId));

        LocalDateTime from = periodStart.atStartOfDay();
        LocalDateTime to = periodEnd.plusDays(1).atStartOfDay();
        List<SettlementEntry> entries = settlementEntryRepository.findByVendorIdAndDateRange(vendorId, from, to);

        StringBuilder sb = new StringBuilder();
        // Header
        sb.append(escapeCsvCell("Entry ID")).append(",")
                .append(escapeCsvCell("Date")).append(",")
                .append(escapeCsvCell("Type")).append(",")
                .append(escapeCsvCell("Reference")).append(",")
                .append(escapeCsvCell("Order ID")).append(",")
                .append(escapeCsvCell("Dispute ID")).append(",")
                .append(escapeCsvCell("Settlement ID")).append(",")
                .append(escapeCsvCell("Amount"))
                .append("\r\n");

        BigDecimal total = BigDecimal.ZERO;

        for (SettlementEntry entry : entries) {
            BigDecimal amount = entry.getAmount() != null ? entry.getAmount() : BigDecimal.ZERO;
            total = total.add(amount);

            String formattedDate = entry.getCreatedAt() != null ? entry.getCreatedAt().format(DATE_TIME_FORMATTER) : "";
            String orderIdStr = entry.getOrder() != null && entry.getOrder().getId() != null
                    ? String.valueOf(entry.getOrder().getId()) : "";
            String disputeIdStr = entry.getDispute() != null && entry.getDispute().getId() != null
                    ? String.valueOf(entry.getDispute().getId()) : "";
            String settlementIdStr = entry.getSettlement() != null && entry.getSettlement().getId() != null
                    ? String.valueOf(entry.getSettlement().getId()) : "";

            sb.append(escapeCsvCell(String.valueOf(entry.getId()))).append(",")
                    .append(escapeCsvCell(formattedDate)).append(",")
                    .append(escapeCsvCell(entry.getType() != null ? entry.getType().name() : "")).append(",")
                    .append(escapeCsvCell(entry.getReference() != null ? entry.getReference() : "")).append(",")
                    .append(escapeCsvCell(orderIdStr)).append(",")
                    .append(escapeCsvCell(disputeIdStr)).append(",")
                    .append(escapeCsvCell(settlementIdStr)).append(",")
                    .append(escapeCsvCell(amount.setScale(2, RoundingMode.HALF_UP).toPlainString()))
                    .append("\r\n");
        }

        // Summary total row
        sb.append(escapeCsvCell("TOTAL")).append(",")
                .append(escapeCsvCell("")).append(",")
                .append(escapeCsvCell("")).append(",")
                .append(escapeCsvCell("")).append(",")
                .append(escapeCsvCell("")).append(",")
                .append(escapeCsvCell("")).append(",")
                .append(escapeCsvCell("")).append(",")
                .append(escapeCsvCell(total.setScale(2, RoundingMode.HALF_UP).toPlainString()))
                .append("\r\n");

        log.info("Generated CSV statement for vendor {} ({}) from {} to {}: {} entries, total {}",
                vendorId, vendor.getBusinessName(), periodStart, periodEnd, entries.size(), total);

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Sanitizes and escapes a cell for safe CSV export adhering to OWASP injection mitigation.
     *
     * <ol>
     *   <li>Nulls are rendered as empty quoted cell {@code ""}.</li>
     *   <li>Cells starting with {@code =}, {@code +}, {@code -}, {@code @}, tab ({@code \t}), or CR ({@code \r})
     *       are prepended with a single apostrophe {@code '} to deactivate formula execution.</li>
     *   <li>Internal double quotes are escaped as {@code ""}.</li>
     *   <li>The entire value is wrapped in double quotes.</li>
     * </ol>
     */
    public static String escapeCsvCell(String value) {
        if (value == null) {
            return "\"\"";
        }
        String sanitized = value;
        if (!sanitized.isEmpty()) {
            char first = sanitized.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
                sanitized = "'" + sanitized;
            }
        }
        String escaped = sanitized.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
