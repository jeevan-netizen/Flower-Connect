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

import com.flowerconnect.billing.dto.OverdueVendorResponse;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverdueDuesServiceTest {

    @Mock
    private SettlementEntryRepository settlementEntryRepository;

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    private AppProperties appProperties;
    private Clock fixedClock;
    private OverdueDuesService overdueDuesService;

    // Fixed test time: 2026-10-26
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.setOverdueDays(30);
        fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        overdueDuesService = new OverdueDuesService(
                settlementEntryRepository,
                vendorProfileRepository,
                appProperties,
                fixedClock
        );
    }

    @Test
    @DisplayName("No open negative entries returns empty list")
    void noOpenNegativeEntriesReturnsEmpty() {
        when(settlementEntryRepository.findDistinctVendorIdsWithOpenNegativeEntries()).thenReturn(List.of());

        List<OverdueVendorResponse> result = overdueDuesService.getOverdueVendors();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Vendor with positive net balance is not overdue even if negative entry exists")
    void positiveNetBalanceNotOverdue() {
        when(settlementEntryRepository.findDistinctVendorIdsWithOpenNegativeEntries()).thenReturn(List.of(10L));
        VendorProfile profile = VendorProfile.builder().id(10L).businessName("Floral Studio").build();
        when(vendorProfileRepository.findAllById(List.of(10L))).thenReturn(List.of(profile));
        // Net positive balance (+120.00)
        when(settlementEntryRepository.calculateOutstandingBalance(10L)).thenReturn(new BigDecimal("120.00"));

        List<OverdueVendorResponse> result = overdueDuesService.getOverdueVendors();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Vendor with negative net balance under 30 days is not overdue")
    void negativeBalanceUnder30DaysNotOverdue() {
        when(settlementEntryRepository.findDistinctVendorIdsWithOpenNegativeEntries()).thenReturn(List.of(10L));
        VendorProfile profile = VendorProfile.builder().id(10L).businessName("Floral Studio").build();
        when(vendorProfileRepository.findAllById(List.of(10L))).thenReturn(List.of(profile));
        when(settlementEntryRepository.calculateOutstandingBalance(10L)).thenReturn(new BigDecimal("-50.00"));

        // Negative entry created 15 days ago (2026-10-11)
        LocalDateTime fifteenDaysAgo = LocalDateTime.of(2026, 10, 11, 10, 0);
        when(settlementEntryRepository.findOldestOpenNegativeEntryDate(10L)).thenReturn(Optional.of(fifteenDaysAgo));

        List<OverdueVendorResponse> result = overdueDuesService.getOverdueVendors();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Vendor with negative net balance at or over 30 days is overdue")
    void negativeBalanceOver30DaysIsOverdue() {
        when(settlementEntryRepository.findDistinctVendorIdsWithOpenNegativeEntries()).thenReturn(List.of(10L));
        VendorProfile profile = VendorProfile.builder().id(10L).businessName("Rose Garden").build();
        when(vendorProfileRepository.findAllById(List.of(10L))).thenReturn(List.of(profile));
        when(settlementEntryRepository.calculateOutstandingBalance(10L)).thenReturn(new BigDecimal("-175.50"));

        // Negative entry created 35 days ago (2026-09-21)
        LocalDateTime thirtyFiveDaysAgo = LocalDateTime.of(2026, 9, 21, 10, 0);
        when(settlementEntryRepository.findOldestOpenNegativeEntryDate(10L)).thenReturn(Optional.of(thirtyFiveDaysAgo));

        List<OverdueVendorResponse> result = overdueDuesService.getOverdueVendors();

        assertThat(result).hasSize(1);
        OverdueVendorResponse item = result.get(0);
        assertThat(item.getVendorId()).isEqualTo(10L);
        assertThat(item.getVendorBusinessName()).isEqualTo("Rose Garden");
        assertThat(item.getOutstandingBalance()).isEqualTo(new BigDecimal("-175.50"));
        assertThat(item.getOutstandingDues()).isEqualTo(new BigDecimal("175.50"));
        assertThat(item.getDaysOverdue()).isEqualTo(35);
        assertThat(item.getOldestDuesDate()).isEqualTo(thirtyFiveDaysAgo);
    }

    @Test
    @DisplayName("Multiple overdue vendors are sorted by days overdue descending")
    void multipleVendorsSortedByDaysOverdueDesc() {
        when(settlementEntryRepository.findDistinctVendorIdsWithOpenNegativeEntries()).thenReturn(List.of(10L, 20L));
        VendorProfile profile1 = VendorProfile.builder().id(10L).businessName("Vendor 10").build();
        VendorProfile profile2 = VendorProfile.builder().id(20L).businessName("Vendor 20").build();
        when(vendorProfileRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(profile1, profile2));

        // Vendor 10: 31 days overdue (-60.00)
        when(settlementEntryRepository.calculateOutstandingBalance(10L)).thenReturn(new BigDecimal("-60.00"));
        when(settlementEntryRepository.findOldestOpenNegativeEntryDate(10L))
                .thenReturn(Optional.of(LocalDateTime.of(2026, 9, 25, 10, 0))); // 31 days

        // Vendor 20: 45 days overdue (-120.00)
        when(settlementEntryRepository.calculateOutstandingBalance(20L)).thenReturn(new BigDecimal("-120.00"));
        when(settlementEntryRepository.findOldestOpenNegativeEntryDate(20L))
                .thenReturn(Optional.of(LocalDateTime.of(2026, 9, 11, 10, 0))); // 45 days

        List<OverdueVendorResponse> result = overdueDuesService.getOverdueVendors();

        assertThat(result).hasSize(2);
        // Vendor 20 (45 days) first, then Vendor 10 (31 days)
        assertThat(result.get(0).getVendorId()).isEqualTo(20L);
        assertThat(result.get(0).getDaysOverdue()).isEqualTo(45);
        assertThat(result.get(1).getVendorId()).isEqualTo(10L);
        assertThat(result.get(1).getDaysOverdue()).isEqualTo(31);
    }
}
