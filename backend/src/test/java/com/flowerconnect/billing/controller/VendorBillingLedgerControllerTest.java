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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VendorBillingLedgerController.class)
@Import({VendorBillingLedgerControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorBillingLedgerControllerTest {

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
    private VendorProfileRepository vendorProfileRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private BillingLedgerService billingLedgerService;

    @MockBean
    private StatementService statementService;

    private RoleBoundaryTester roleBoundaryTester;
    private User vendorUser;
    private VendorProfile vendor;

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
                .build();
        when(userRepository.findByEmailWithRole("florist@test.com")).thenReturn(Optional.of(vendorUser));
        when(userRepository.findByEmailWithRole("test-florist@test.com")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUserEmail("florist@test.com")).thenReturn(Optional.of(vendor));
        when(vendorProfileRepository.findByUserEmail("test-florist@test.com")).thenReturn(Optional.of(vendor));
    }

    @Test
    @DisplayName("GET /api/v1/vendor/billing/ledger enforces FLORIST RBAC")
    void ledgerRbacBoundary() {
        when(vendorProfileRepository.findByUserEmail("test-florist@test.com")).thenReturn(Optional.of(vendor));
        when(billingLedgerService.getVendorLedger(any(), any(), any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());

        roleBoundaryTester.assertUnauthenticated("/api/v1/vendor/billing/ledger", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/vendor/billing/ledger", HttpMethod.GET, 403, 200, 403);
    }

    @Test
    @DisplayName("GET /api/v1/vendor/billing/outstanding enforces FLORIST RBAC")
    void outstandingRbacBoundary() {
        when(vendorProfileRepository.findByUserEmail("test-florist@test.com")).thenReturn(Optional.of(vendor));
        when(billingLedgerService.getOutstandingBalance(any())).thenReturn(BigDecimal.ZERO);

        roleBoundaryTester.assertUnauthenticated("/api/v1/vendor/billing/outstanding", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/vendor/billing/outstanding", HttpMethod.GET, 403, 200, 403);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    @DisplayName("GET /api/v1/vendor/billing/ledger returns 200 with paginated entries")
    void getLedgerReturns200() throws Exception {
        when(userRepository.findByEmailWithRole("florist@test.com")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUserEmail("florist@test.com")).thenReturn(Optional.of(vendor));

        SettlementEntry entry = SettlementEntry.builder()
                .id(1L)
                .vendor(vendor)
                .type(SettlementEntryType.ONLINE_ORDER_CREDIT)
                .amount(new BigDecimal("110.00"))
                .reference("Online order credit")
                .createdAt(LocalDateTime.now())
                .build();

        when(billingLedgerService.getVendorLedger(eq(20L), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        mockMvc.perform(get("/api/v1/vendor/billing/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].type").value("ONLINE_ORDER_CREDIT"))
                .andExpect(jsonPath("$.content[0].amount").value(110.00));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    @DisplayName("GET /api/v1/vendor/billing/outstanding returns 200 with signed balance")
    void getOutstandingReturns200() throws Exception {
        when(userRepository.findByEmailWithRole("florist@test.com")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUserEmail("florist@test.com")).thenReturn(Optional.of(vendor));
        when(billingLedgerService.getOutstandingBalance(20L)).thenReturn(new BigDecimal("70.00"));

        mockMvc.perform(get("/api/v1/vendor/billing/outstanding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendorId").value(20))
                .andExpect(jsonPath("$.outstandingBalance").value(70.00));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    @DisplayName("GET /api/v1/vendor/billing/statement returns CSV file attachment")
    void getStatementReturnsCsv() throws Exception {
        byte[] sampleCsv = "\"Entry ID\",\"Amount\"\r\n\"1\",\"100.00\"\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(statementService.generateVendorStatementCsv(eq(20L), any(), any())).thenReturn(sampleCsv);

        mockMvc.perform(get("/api/v1/vendor/billing/statement")
                        .param("periodStart", "2026-10-01")
                        .param("periodEnd", "2026-10-07"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "text/csv; charset=UTF-8"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"statement-vendor-20-2026-10-01-2026-10-07.csv\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(sampleCsv));
    }
}
