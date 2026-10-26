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

import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementServiceTest {

    @Mock
    private SettlementEntryRepository settlementEntryRepository;

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @InjectMocks
    private StatementService statementService;

    private VendorProfile vendor;
    private final LocalDate start = LocalDate.of(2026, 10, 1);
    private final LocalDate end = LocalDate.of(2026, 10, 7);

    @BeforeEach
    void setUp() {
        vendor = VendorProfile.builder()
                .id(10L)
                .businessName("Petals & Blooms")
                .build();
    }

    @Test
    @DisplayName("OWASP CSV cell escaping mitigates formula injection across all triggers (=, +, -, @, tab, cr)")
    void escapeCsvCell_owaspMitigation() {
        // Starts with =
        assertThat(StatementService.escapeCsvCell("=1+1")).isEqualTo("\"'=1+1\"");
        assertThat(StatementService.escapeCsvCell("=cmd|' /C calc'!A0")).isEqualTo("\"'=cmd|' /C calc'!A0\"");

        // Starts with +
        assertThat(StatementService.escapeCsvCell("+123")).isEqualTo("\"'+123\"");

        // Starts with -
        assertThat(StatementService.escapeCsvCell("-45.00")).isEqualTo("\"'-45.00\"");

        // Starts with @
        assertThat(StatementService.escapeCsvCell("@SUM(A1:A10)")).isEqualTo("\"'@SUM(A1:A10)\"");

        // Starts with \t (tab)
        assertThat(StatementService.escapeCsvCell("\tmalicious")).isEqualTo("\"'\tmalicious\"");

        // Starts with \r (carriage return)
        assertThat(StatementService.escapeCsvCell("\rmalicious")).isEqualTo("\"'\rmalicious\"");

        // Internal quotes escaped as ""
        assertThat(StatementService.escapeCsvCell("Flower \"Special\" Rose")).isEqualTo("\"Flower \"\"Special\"\" Rose\"");

        // Regular safe text
        assertThat(StatementService.escapeCsvCell("Standard Product")).isEqualTo("\"Standard Product\"");

        // Null and empty
        assertThat(StatementService.escapeCsvCell(null)).isEqualTo("\"\"");
        assertThat(StatementService.escapeCsvCell("")).isEqualTo("\"\"");
    }

    @Test
    @DisplayName("generateVendorStatementCsv generates proper CSV structure with header, data rows, and total")
    void generateVendorStatementCsv_success() {
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));

        Order order1 = Order.builder().id(101L).build();
        Settlement settlement = Settlement.builder().id(501L).build();

        SettlementEntry entry1 = SettlementEntry.builder()
                .id(1L)
                .vendor(vendor)
                .order(order1)
                .type(SettlementEntryType.ONLINE_ORDER_CREDIT)
                .reference("ORD-101")
                .amount(new BigDecimal("110.00"))
                .settlement(settlement)
                .createdAt(LocalDateTime.of(2026, 10, 2, 14, 30, 0))
                .build();

        SettlementEntry entry2 = SettlementEntry.builder()
                .id(2L)
                .vendor(vendor)
                .type(SettlementEntryType.COD_DUES)
                .reference("COD order fee")
                .amount(new BigDecimal("-10.00"))
                .createdAt(LocalDateTime.of(2026, 10, 3, 16, 0, 0))
                .build();

        when(settlementEntryRepository.findByVendorIdAndDateRange(eq(10L), any(), any()))
                .thenReturn(List.of(entry1, entry2));

        byte[] csvBytes = statementService.generateVendorStatementCsv(10L, start, end);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(4); // Header, entry1, entry2, Total

        // Header check
        assertThat(lines[0]).isEqualTo("\"Entry ID\",\"Date\",\"Type\",\"Reference\",\"Order ID\",\"Dispute ID\",\"Settlement ID\",\"Amount\"");

        // Entry 1 row
        assertThat(lines[1]).contains("\"1\"")
                .contains("\"2026-10-02 14:30:00\"")
                .contains("\"ONLINE_ORDER_CREDIT\"")
                .contains("\"ORD-101\"")
                .contains("\"101\"")
                .contains("\"501\"")
                .contains("\"110.00\"");

        // Entry 2 row (-10.00 must be prefixed with ' due to starting with -)
        assertThat(lines[2]).contains("\"2\"")
                .contains("\"2026-10-03 16:00:00\"")
                .contains("\"COD_DUES\"")
                .contains("\"COD order fee\"")
                .contains("\"'-10.00\"");

        // Total row: 110.00 - 10.00 = 100.00
        assertThat(lines[3]).isEqualTo("\"TOTAL\",\"\",\"\",\"\",\"\",\"\",\"\",\"100.00\"");
    }

    @Test
    @DisplayName("generateVendorStatementCsv throws BAD_REQUEST when periodStart is after periodEnd")
    void generateVendorStatementCsv_invalidDates() {
        assertThatThrownBy(() -> statementService.generateVendorStatementCsv(10L, end, start))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("periodStart must be on or before periodEnd");
    }

    @Test
    @DisplayName("generateVendorStatementCsv throws NOT_FOUND when vendor does not exist")
    void generateVendorStatementCsv_vendorNotFound() {
        when(vendorProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> statementService.generateVendorStatementCsv(99L, start, end))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vendor not found");
    }
}
