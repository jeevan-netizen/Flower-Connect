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
import com.flowerconnect.billing.service.BillingLedgerService;
import com.flowerconnect.billing.service.StatementService;
import com.flowerconnect.config.TestClockConfig;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminBillingLedgerController.class)
@Import({AdminBillingLedgerControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class AdminBillingLedgerControllerTest {

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

    @MockBean
    private BillingLedgerService billingLedgerService;

    @MockBean
    private StatementService statementService;

    @MockBean
    private com.flowerconnect.billing.service.OverdueDuesService overdueDuesService;

    private RoleBoundaryTester roleBoundaryTester;

    @BeforeEach
    void setUp() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);
    }

    @Test
    @DisplayName("GET /api/v1/admin/billing/ledger enforces ADMIN RBAC")
    void ledgerRbacBoundary() {
        when(billingLedgerService.getAllLedger(any(), any(), any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        roleBoundaryTester.assertUnauthenticated("/api/v1/admin/billing/ledger", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/admin/billing/ledger", HttpMethod.GET, 403, 403, 200);
    }

    @Test
    @DisplayName("GET /api/v1/admin/billing/outstanding/{vendorId} enforces ADMIN RBAC")
    void outstandingRbacBoundary() {
        when(billingLedgerService.getOutstandingBalance(eq(20L))).thenReturn(BigDecimal.ZERO);

        roleBoundaryTester.assertUnauthenticated("/api/v1/admin/billing/outstanding/20", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/admin/billing/outstanding/20", HttpMethod.GET, 403, 403, 200);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/billing/ledger returns 200 with all entries")
    void getAllLedgerReturns200() throws Exception {
        VendorProfile vendor = VendorProfile.builder().id(20L).build();
        SettlementEntry entry = SettlementEntry.builder()
                .id(1L)
                .vendor(vendor)
                .type(SettlementEntryType.COD_DUES)
                .amount(new BigDecimal("-10.00"))
                .reference("COD order dues")
                .createdAt(LocalDateTime.now())
                .build();

        when(billingLedgerService.getAllLedger(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        mockMvc.perform(get("/api/v1/admin/billing/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].type").value("COD_DUES"))
                .andExpect(jsonPath("$.content[0].amount").value(-10.00));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/billing/outstanding/{vendorId} returns 200 with vendor's balance")
    void getOutstandingReturns200() throws Exception {
        when(billingLedgerService.getOutstandingBalance(20L)).thenReturn(new BigDecimal("-50.00"));

        mockMvc.perform(get("/api/v1/admin/billing/outstanding/20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(20))
                .andExpect(jsonPath("$.outstandingBalance").value(-50.00));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/billing/statement returns CSV file attachment for any vendor")
    void getStatementReturnsCsv() throws Exception {
        byte[] sampleCsv = "\"Entry ID\",\"Amount\"\r\n\"1\",\"100.00\"\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(statementService.generateVendorStatementCsv(eq(20L), any(), any())).thenReturn(sampleCsv);

        mockMvc.perform(get("/api/v1/admin/billing/statement")
                        .param("vendorId", "20")
                        .param("periodStart", "2026-10-01")
                        .param("periodEnd", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"statement-vendor-20-2026-10-01-2026-10-07.csv\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(sampleCsv));
    }

    @Test
    @DisplayName("GET /api/v1/admin/billing/overdue enforces ADMIN RBAC")
    void overdueRbacBoundary() {
        when(overdueDuesService.getOverdueVendors()).thenReturn(List.of());

        roleBoundaryTester.assertUnauthenticated("/api/v1/admin/billing/overdue", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/admin/billing/overdue", HttpMethod.GET, 403, 403, 200);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    @DisplayName("GET /api/v1/admin/billing/overdue returns 200 with list of overdue vendors")
    void getOverdueVendorsReturns200() throws Exception {
        com.flowerconnect.billing.dto.OverdueVendorResponse item = com.flowerconnect.billing.dto.OverdueVendorResponse.builder()
                .vendorId(10L)
                .vendorBusinessName("Rose Garden")
                .outstandingBalance(new BigDecimal("-150.00"))
                .outstandingDues(new BigDecimal("150.00"))
                .daysOverdue(35)
                .oldestDuesDate(LocalDateTime.of(2026, 9, 21, 10, 0))
                .build();

        when(overdueDuesService.getOverdueVendors()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/admin/billing/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vendorId").value(10))
                .andExpect(jsonPath("$[0].vendorBusinessName").value("Rose Garden"))
                .andExpect(jsonPath("$[0].outstandingBalance").value(-150.00))
                .andExpect(jsonPath("$[0].outstandingDues").value(150.00))
                .andExpect(jsonPath("$[0].daysOverdue").value(35));
    }
}
