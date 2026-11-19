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

/**
 * Regression test for the billing/settlement {@code LazyInitializationException}
 * caused by {@code spring.jpa.open-in-view: false} combined with
 * {@code FetchType.LAZY} relationships on {@code Settlement} and
 * {@code SettlementEntry}. The admin billing endpoints return JPA entities from
 * {@code @Transactional(readOnly = true)} services and map them to DTOs in the
 * controller after the transaction has closed, so accessing lazy relationships
 * (e.g. {@code settlement.getVendor()}, {@code entry.getVendor()}) outside the
 * transaction throws {@code LazyInitializationException} and surfaces as a 500
 * ("Something went wrong") on the Admin Billing & Settlements page.
 *
 * <p>These tests use the real service layer and real database (Testcontainers
 * MySQL) to exercise the actual controller-to-service-to-repository path.
 */
package com.flowerconnect.billing.integration;

import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementDirection;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.billing.repository.SettlementRepository;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.hamcrest.Matchers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin-lazy@test.com", roles = {"ADMIN"})
class BillingLazyLoadIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementEntryRepository settlementEntryRepository;

    @Autowired
    private VendorProfileRepository vendorProfileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ServiceLocationRepository serviceLocationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private VendorProfile vendor;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        Role floristRole = roleRepository.findByName("FLORIST").orElseThrow();
        User vendorUser = userRepository.saveAndFlush(User.builder()
                .email("vendor-lazy-" + UUID.randomUUID() + "@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .fullName("Vendor Lazy User")
                .phone("+91" + randomDigits(10))
                .role(floristRole)
                .status(User.Status.ACTIVE)
                .build());

        var location = serviceLocationRepository.findAll().stream().findFirst()
                .orElseGet(() -> serviceLocationRepository.saveAndFlush(
                        com.flowerconnect.domain.ServiceLocation.builder()
                                .city("Bengaluru")
                                .area("Indiranagar")
                                .pincode("560038")
                                .latitude(BigDecimal.valueOf(12.97))
                                .longitude(BigDecimal.valueOf(77.64))
                                .build()));

        vendor = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vendorUser)
                .businessName("Lazy Load Florist " + UUID.randomUUID())
                .addressLine1("1 Lazy Lane")
                .serviceLocation(location)
                .latitude(BigDecimal.valueOf(12.97))
                .longitude(BigDecimal.valueOf(77.64))
                .deliveryRadiusKm(new BigDecimal("10.00"))
                .status(VendorProfile.Status.APPROVED)
                .commissionRate(new BigDecimal("10.00"))
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());

        settlement = settlementRepository.saveAndFlush(Settlement.builder()
                .vendor(vendor)
                .periodStart(LocalDate.of(2026, 10, 1))
                .periodEnd(LocalDate.of(2026, 10, 7))
                .totalCredits(new BigDecimal("110.00"))
                .totalDues(new BigDecimal("20.00"))
                .netAmount(new BigDecimal("90.00"))
                .direction(SettlementDirection.PLATFORM_PAYS_VENDOR)
                .status(SettlementStatus.PENDING)
                .build());

        settlementEntryRepository.saveAndFlush(SettlementEntry.builder()
                .vendor(vendor)
                .type(SettlementEntryType.ONLINE_ORDER_CREDIT)
                .amount(new BigDecimal("110.00"))
                .reference("Online order credit")
                .createdAt(LocalDateTime.now())
                .build());

        settlementEntryRepository.saveAndFlush(SettlementEntry.builder()
                .vendor(vendor)
                .type(SettlementEntryType.COD_DUES)
                .amount(new BigDecimal("-20.00"))
                .reference("COD order dues")
                .createdAt(LocalDateTime.now())
                .build());
    }

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }

    @Test
    @DisplayName("GET /api/v1/admin/billing/settlements returns 200 without LazyInitializationException")
    void listSettlementsDoesNotThrowLazyInitializationException() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/billing/ledger returns 200 without LazyInitializationException")
    void getAllLedgerDoesNotThrowLazyInitializationException() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("GET /api/v1/admin/billing/settlements/{id} returns 200 without LazyInitializationException")
    void getSettlementDetailDoesNotThrowLazyInitializationException() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/settlements/{id}", settlement.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.id").value(settlement.getId()))
                .andExpect(jsonPath("$.settlement.vendorBusinessName").value(vendor.getBusinessName()))
                .andExpect(jsonPath("$.entries").isArray());
    }

    @Test
    @DisplayName("Settlement response includes vendor business name (lazy relationship resolved)")
    void settlementResponseIncludesVendorBusinessName() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/settlements/{id}", settlement.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.vendorBusinessName").value(vendor.getBusinessName()))
                .andExpect(jsonPath("$.settlement.vendorId").value(vendor.getId().intValue()));
    }

    @Test
    @DisplayName("Ledger entry response includes vendor ID (lazy relationship resolved)")
    void ledgerEntryResponseIncludesVendorId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/billing/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", Matchers.hasSize(Matchers.greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.content[0].vendorId").value(vendor.getId().intValue()))
                .andExpect(jsonPath("$.content[0].type").isNotEmpty());
    }
}
