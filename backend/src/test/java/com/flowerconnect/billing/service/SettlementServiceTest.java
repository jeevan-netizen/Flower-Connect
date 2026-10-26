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
import com.flowerconnect.billing.domain.SettlementDirection;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.billing.repository.SettlementRepository;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementEntryRepository settlementEntryRepository;

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private SettlementService settlementService;

    private VendorProfile vendor;
    private User adminUser;
    private final LocalDate start = LocalDate.of(2026, 10, 12);
    private final LocalDate end = LocalDate.of(2026, 10, 18);

    @BeforeEach
    void setUp() {
        vendor = VendorProfile.builder()
                .id(10L)
                .businessName("Petals & Blooms")
                .build();

        adminUser = User.builder()
                .id(1L)
                .email("admin@flowerconnect.com")
                .role(Role.builder().name("ADMIN").build())
                .build();
    }

    @Test
    @DisplayName("runSettlement calculates positive net and sets PLATFORM_PAYS_VENDOR direction")
    void runSettlement_platformPaysVendor() {
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));
        when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(10L, start, end))
                .thenReturn(Optional.empty());

        SettlementEntry entry1 = SettlementEntry.builder()
                .id(101L)
                .vendor(vendor)
                .type(SettlementEntryType.ONLINE_ORDER_CREDIT)
                .amount(new BigDecimal("110.00"))
                .build();
        SettlementEntry entry2 = SettlementEntry.builder()
                .id(102L)
                .vendor(vendor)
                .type(SettlementEntryType.COD_DUES)
                .amount(new BigDecimal("-10.00"))
                .build();
        SettlementEntry entry3 = SettlementEntry.builder()
                .id(103L)
                .vendor(vendor)
                .type(SettlementEntryType.DISPUTE_ADJUSTMENT)
                .amount(new BigDecimal("-30.00"))
                .build();

        List<SettlementEntry> entries = new ArrayList<>(List.of(entry1, entry2, entry3));
        when(settlementEntryRepository.findEligibleUnattachedForSettlement(eq(10L), any(LocalDateTime.class)))
                .thenReturn(entries);

        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> {
            Settlement s = inv.getArgument(0);
            return Settlement.builder()
                    .id(501L)
                    .vendor(s.getVendor())
                    .periodStart(s.getPeriodStart())
                    .periodEnd(s.getPeriodEnd())
                    .totalCredits(s.getTotalCredits())
                    .totalDues(s.getTotalDues())
                    .netAmount(s.getNetAmount())
                    .direction(s.getDirection())
                    .status(s.getStatus())
                    .build();
        });

        Settlement result = settlementService.runSettlement(10L, start, end);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(501L);
        assertThat(result.getTotalCredits()).isEqualByComparingTo("110.00");
        assertThat(result.getTotalDues()).isEqualByComparingTo("40.00");
        assertThat(result.getNetAmount()).isEqualByComparingTo("70.00");
        assertThat(result.getDirection()).isEqualTo(SettlementDirection.PLATFORM_PAYS_VENDOR);
        assertThat(result.getStatus()).isEqualTo(SettlementStatus.PENDING);

        // Verify entries attached
        assertThat(entry1.getSettlement()).isNotNull();
        assertThat(entry1.getSettlement().getId()).isEqualTo(501L);
        assertThat(entry2.getSettlement()).isNotNull();
        assertThat(entry3.getSettlement()).isNotNull();
        verify(settlementEntryRepository).saveAll(entries);
    }

    @Test
    @DisplayName("runSettlement calculates negative net and sets VENDOR_PAYS_PLATFORM direction")
    void runSettlement_vendorPaysPlatform() {
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));
        when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(10L, start, end))
                .thenReturn(Optional.empty());

        SettlementEntry entry1 = SettlementEntry.builder()
                .id(101L)
                .vendor(vendor)
                .type(SettlementEntryType.ONLINE_ORDER_CREDIT)
                .amount(new BigDecimal("20.00"))
                .build();
        SettlementEntry entry2 = SettlementEntry.builder()
                .id(102L)
                .vendor(vendor)
                .type(SettlementEntryType.COD_DUES)
                .amount(new BigDecimal("-50.00"))
                .build();

        List<SettlementEntry> entries = new ArrayList<>(List.of(entry1, entry2));
        when(settlementEntryRepository.findEligibleUnattachedForSettlement(eq(10L), any(LocalDateTime.class)))
                .thenReturn(entries);

        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        Settlement result = settlementService.runSettlement(10L, start, end);

        assertThat(result.getTotalCredits()).isEqualByComparingTo("20.00");
        assertThat(result.getTotalDues()).isEqualByComparingTo("50.00");
        assertThat(result.getNetAmount()).isEqualByComparingTo("-30.00");
        assertThat(result.getDirection()).isEqualTo(SettlementDirection.VENDOR_PAYS_PLATFORM);
    }

    @Test
    @DisplayName("runSettlement with no entries produces ZERO direction and 0.00 netAmount")
    void runSettlement_zeroEntries() {
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));
        when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(10L, start, end))
                .thenReturn(Optional.empty());
        when(settlementEntryRepository.findEligibleUnattachedForSettlement(eq(10L), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        Settlement result = settlementService.runSettlement(10L, start, end);

        assertThat(result.getTotalCredits()).isEqualByComparingTo("0.00");
        assertThat(result.getTotalDues()).isEqualByComparingTo("0.00");
        assertThat(result.getNetAmount()).isEqualByComparingTo("0.00");
        assertThat(result.getDirection()).isEqualTo(SettlementDirection.ZERO);
    }

    @Test
    @DisplayName("runSettlement returns existing PENDING settlement idempotently")
    void runSettlement_idempotentPending() {
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));
        Settlement existing = Settlement.builder()
                .id(300L)
                .vendor(vendor)
                .periodStart(start)
                .periodEnd(end)
                .status(SettlementStatus.PENDING)
                .build();
        when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(10L, start, end))
                .thenReturn(Optional.of(existing));

        Settlement result = settlementService.runSettlement(10L, start, end);

        assertThat(result.getId()).isEqualTo(300L);
        verify(settlementRepository, never()).save(any());
        verify(settlementEntryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("runSettlement throws 409 CONFLICT when settlement is already SETTLED")
    void runSettlement_alreadySettled_throwsConflict() {
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));
        Settlement existing = Settlement.builder()
                .id(300L)
                .vendor(vendor)
                .periodStart(start)
                .periodEnd(end)
                .status(SettlementStatus.SETTLED)
                .build();
        when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(10L, start, end))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> settlementService.runSettlement(10L, start, end))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already settled");
    }

    @Test
    @DisplayName("runSettlement throws BAD_REQUEST when periodStart is after periodEnd")
    void runSettlement_invalidDates_throwsBadRequest() {
        assertThatThrownBy(() -> settlementService.runSettlement(10L, end, start))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("periodStart must be on or before periodEnd");
    }

    @Test
    @DisplayName("runSettlement throws NOT_FOUND when vendor does not exist")
    void runSettlement_vendorNotFound_throwsNotFound() {
        when(vendorProfileRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.runSettlement(99L, start, end))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Vendor not found");
    }

    @Test
    @DisplayName("runSettlementForAllVendors runs settlement for all registered vendors")
    void runSettlementForAllVendors_runsAll() {
        VendorProfile v2 = VendorProfile.builder().id(20L).businessName("Rose Garden").build();
        when(vendorProfileRepository.findAll()).thenReturn(List.of(vendor, v2));

        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.findById(20L)).thenReturn(Optional.of(v2));

        when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(eq(10L), eq(start), eq(end)))
                .thenReturn(Optional.empty());
        when(settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(eq(20L), eq(start), eq(end)))
                .thenReturn(Optional.empty());

        when(settlementEntryRepository.findEligibleUnattachedForSettlement(any(), any())).thenReturn(List.of());
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Settlement> results = settlementService.runSettlementForAllVendors(start, end);

        assertThat(results).hasSize(2);
    }

    @Test
    @DisplayName("finalizeSettlement moves status from PENDING to SETTLED and records audit log")
    void finalizeSettlement_success() {
        Settlement settlement = Settlement.builder()
                .id(500L)
                .vendor(vendor)
                .periodStart(start)
                .periodEnd(end)
                .status(SettlementStatus.PENDING)
                .build();

        when(settlementRepository.findById(500L)).thenReturn(Optional.of(settlement));
        when(userRepository.findByEmailWithRole("admin@flowerconnect.com")).thenReturn(Optional.of(adminUser));
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(inv -> inv.getArgument(0));

        Settlement finalized = settlementService.finalizeSettlement(500L, "admin@flowerconnect.com", "Bank Ref #UTR123456");

        assertThat(finalized.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        assertThat(finalized.getSettledBy()).isEqualTo(adminUser);
        assertThat(finalized.getAdminReferenceNote()).isEqualTo("Bank Ref #UTR123456");
        assertThat(finalized.getSettledAt()).isEqualTo(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActor()).isEqualTo(adminUser);
        assertThat(audit.getActionType()).isEqualTo("SETTLEMENT_FINALIZED");
        assertThat(audit.getEntityType()).isEqualTo("SETTLEMENT");
        assertThat(audit.getEntityId()).isEqualTo(500L);
        assertThat(audit.getReason()).isEqualTo("Bank Ref #UTR123456");
        assertThat(audit.getPreviousValue()).isEqualTo("PENDING");
        assertThat(audit.getNewValue()).isEqualTo("SETTLED");
    }

    @Test
    @DisplayName("finalizeSettlement throws CONFLICT if already SETTLED")
    void finalizeSettlement_alreadySettled_throwsConflict() {
        Settlement settlement = Settlement.builder()
                .id(500L)
                .vendor(vendor)
                .status(SettlementStatus.SETTLED)
                .build();
        when(settlementRepository.findById(500L)).thenReturn(Optional.of(settlement));

        assertThatThrownBy(() -> settlementService.finalizeSettlement(500L, "admin@flowerconnect.com", "Note"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already settled");
    }

    @Test
    @DisplayName("finalizeSettlement throws BAD_REQUEST if admin reference note is blank")
    void finalizeSettlement_blankNote_throwsBadRequest() {
        assertThatThrownBy(() -> settlementService.finalizeSettlement(500L, "admin@flowerconnect.com", "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Admin reference note is required");
    }
}
