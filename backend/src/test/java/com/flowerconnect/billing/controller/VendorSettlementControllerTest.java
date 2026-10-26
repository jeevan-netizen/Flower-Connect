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
import com.flowerconnect.billing.domain.SettlementDirection;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.service.SettlementService;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.RoleBoundaryTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VendorSettlementController.class)
@Import({VendorSettlementControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorSettlementControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendor/**").hasRole("FLORIST")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettlementService settlementService;

    @MockBean
    private VendorProfileRepository vendorProfileRepository;

    @MockBean
    private UserRepository userRepository;

    private RoleBoundaryTester roleBoundaryTester;
    private User vendorUser;
    private VendorProfile vendor;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);

        vendorUser = User.builder()
                .id(2L)
                .email("florist@test.com")
                .role(Role.builder().name("FLORIST").build())
                .build();

        vendor = VendorProfile.builder()
                .id(20L)
                .user(vendorUser)
                .businessName("Florist Garden")
                .build();

        settlement = Settlement.builder()
                .id(100L)
                .vendor(vendor)
                .periodStart(LocalDate.of(2026, 10, 12))
                .periodEnd(LocalDate.of(2026, 10, 18))
                .totalCredits(new BigDecimal("110.00"))
                .totalDues(new BigDecimal("10.00"))
                .netAmount(new BigDecimal("100.00"))
                .direction(SettlementDirection.PLATFORM_PAYS_VENDOR)
                .status(SettlementStatus.PENDING)
                .build();

        when(userRepository.findByEmailWithRole("florist@test.com")).thenReturn(Optional.of(vendorUser));
        when(userRepository.findByEmailWithRole("test-florist@test.com")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUserEmail("florist@test.com")).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.findByUserEmail("test-florist@test.com")).thenReturn(Optional.of(vendor));
    }

    @Test
    @DisplayName("GET /api/v1/vendor/billing/settlements enforces FLORIST RBAC")
    void rbacBoundaries() {
        when(settlementService.getVendorSettlements(any(), any())).thenReturn(Page.empty());

        roleBoundaryTester.assertUnauthenticated("/api/v1/vendor/billing/settlements", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/vendor/billing/settlements", HttpMethod.GET, 403, 200, 403);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    @DisplayName("GET /api/v1/vendor/billing/settlements returns vendor settlements")
    void listSettlements() throws Exception {
        when(settlementService.getVendorSettlements(eq(20L), any()))
                .thenReturn(new PageImpl<>(List.of(settlement)));

        mockMvc.perform(get("/api/v1/vendor/billing/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].vendorId").value(20))
                .andExpect(jsonPath("$.content[0].netAmount").value(100.00));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    @DisplayName("GET /api/v1/vendor/billing/settlements/{id} returns detail and entries")
    void getSettlementDetail() throws Exception {
        SettlementEntry entry = SettlementEntry.builder()
                .id(201L)
                .vendor(vendor)
                .type(SettlementEntryType.ONLINE_ORDER_CREDIT)
                .amount(new BigDecimal("110.00"))
                .reference("ORD-101")
                .createdAt(LocalDateTime.of(2026, 10, 15, 12, 0))
                .build();

        when(settlementService.getSettlementById(100L)).thenReturn(settlement);
        when(settlementService.getSettlementEntries(100L)).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/vendor/billing/settlements/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.id").value(100))
                .andExpect(jsonPath("$.entries[0].id").value(201))
                .andExpect(jsonPath("$.entries[0].amount").value(110.00));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    @DisplayName("GET /api/v1/vendor/billing/settlements/{id} returns 403 when accessing another vendor's settlement")
    void crossVendorIsolation() throws Exception {
        VendorProfile otherVendor = VendorProfile.builder().id(999L).build();
        Settlement otherSettlement = Settlement.builder()
                .id(101L)
                .vendor(otherVendor)
                .build();

        when(settlementService.getSettlementById(101L)).thenReturn(otherSettlement);

        mockMvc.perform(get("/api/v1/vendor/billing/settlements/101"))
                .andExpect(status().isForbidden());
    }
}
