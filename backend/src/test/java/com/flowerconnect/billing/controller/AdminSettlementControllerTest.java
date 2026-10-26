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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementDirection;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.dto.FinalizeSettlementRequest;
import com.flowerconnect.billing.dto.RunSettlementRequest;
import com.flowerconnect.billing.service.SettlementService;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
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
import org.springframework.http.MediaType;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminSettlementController.class)
@Import({AdminSettlementControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class AdminSettlementControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SettlementService settlementService;

    private RoleBoundaryTester roleBoundaryTester;
    private VendorProfile vendor;
    private Settlement settlement;

    @BeforeEach
    void setUp() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);

        vendor = VendorProfile.builder()
                .id(10L)
                .businessName("Petals & Blooms")
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
                .createdAt(LocalDateTime.of(2026, 10, 19, 0, 0))
                .build();
    }

    @Test
    @DisplayName("Admin settlement endpoints enforce ADMIN RBAC")
    void rbacBoundaries() {
        when(settlementService.listSettlements(any(), any(), any())).thenReturn(Page.empty());

        roleBoundaryTester.assertUnauthenticated("/api/v1/admin/billing/settlements", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/admin/billing/settlements", HttpMethod.GET, 403, 403, 200);
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("POST /run with vendorId generates settlement for single vendor")
    void runSettlement_singleVendor() throws Exception {
        RunSettlementRequest req = RunSettlementRequest.builder()
                .vendorId(10L)
                .periodStart(LocalDate.of(2026, 10, 12))
                .periodEnd(LocalDate.of(2026, 10, 18))
                .build();

        when(settlementService.runSettlement(10L, req.getPeriodStart(), req.getPeriodEnd()))
                .thenReturn(settlement);

        mockMvc.perform(post("/api/v1/admin/billing/settlements/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].vendorId").value(10))
                .andExpect(jsonPath("$[0].vendorBusinessName").value("Petals & Blooms"))
                .andExpect(jsonPath("$[0].netAmount").value(100.00))
                .andExpect(jsonPath("$[0].direction").value("PLATFORM_PAYS_VENDOR"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("POST /run without vendorId generates settlements for all vendors")
    void runSettlement_allVendors() throws Exception {
        RunSettlementRequest req = RunSettlementRequest.builder()
                .periodStart(LocalDate.of(2026, 10, 12))
                .periodEnd(LocalDate.of(2026, 10, 18))
                .build();

        when(settlementService.runSettlementForAllVendors(req.getPeriodStart(), req.getPeriodEnd()))
                .thenReturn(List.of(settlement));

        mockMvc.perform(post("/api/v1/admin/billing/settlements/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/billing/settlements returns paginated settlements")
    void listSettlements() throws Exception {
        Page<Settlement> page = new PageImpl<>(List.of(settlement));
        when(settlementService.listSettlements(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/billing/settlements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].vendorBusinessName").value("Petals & Blooms"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/billing/settlements/{id} returns detail and entries")
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

        mockMvc.perform(get("/api/v1/admin/billing/settlements/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.id").value(100))
                .andExpect(jsonPath("$.entries[0].id").value(201))
                .andExpect(jsonPath("$.entries[0].amount").value(110.00))
                .andExpect(jsonPath("$.entries[0].reference").value("ORD-101"));
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("PATCH /api/v1/admin/billing/settlements/{id}/finalize moves to SETTLED")
    void finalizeSettlement() throws Exception {
        FinalizeSettlementRequest req = FinalizeSettlementRequest.builder()
                .adminReferenceNote("UTR123456789")
                .build();

        User adminUser = User.builder().id(1L).email("admin@flowerconnect.com").build();
        Settlement finalized = Settlement.builder()
                .id(100L)
                .vendor(vendor)
                .periodStart(LocalDate.of(2026, 10, 12))
                .periodEnd(LocalDate.of(2026, 10, 18))
                .totalCredits(new BigDecimal("110.00"))
                .totalDues(new BigDecimal("10.00"))
                .netAmount(new BigDecimal("100.00"))
                .direction(SettlementDirection.PLATFORM_PAYS_VENDOR)
                .status(SettlementStatus.SETTLED)
                .settledBy(adminUser)
                .settledAt(LocalDateTime.of(2026, 10, 20, 10, 0))
                .adminReferenceNote("UTR123456789")
                .build();

        when(settlementService.finalizeSettlement(eq(100L), eq("admin@flowerconnect.com"), eq("UTR123456789")))
                .thenReturn(finalized);

        mockMvc.perform(patch("/api/v1/admin/billing/settlements/100/finalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.adminReferenceNote").value("UTR123456789"))
                .andExpect(jsonPath("$.settledByEmail").value("admin@flowerconnect.com"));
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("PATCH /api/v1/admin/billing/settlements/{id}/finalize returns 400 when note is blank")
    void finalizeSettlement_blankNote() throws Exception {
        FinalizeSettlementRequest req = FinalizeSettlementRequest.builder()
                .adminReferenceNote("")
                .build();

        mockMvc.perform(patch("/api/v1/admin/billing/settlements/100/finalize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
