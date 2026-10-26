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
import com.flowerconnect.billing.dto.AdminCommissionRateRequest;
import com.flowerconnect.billing.service.CommissionRateService;
import com.flowerconnect.billing.service.PlatformConfigService;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.test.RoleBoundaryTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminCommissionRateController.class)
@Import({AdminCommissionRateControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class AdminCommissionRateControllerTest {

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
    private VendorProfileRepository vendorProfileRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private CommissionRateService commissionRateService;

    @MockBean
    private PlatformConfigService platformConfigService;

    private RoleBoundaryTester roleBoundaryTester;
    private User admin;
    private VendorProfile vendor;

    @BeforeEach
    void setUp() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);
        admin = User.builder()
                .id(1L)
                .email("admin@flowerconnect.com")
                .role(Role.builder().name("ADMIN").build())
                .build();
        vendor = VendorProfile.builder()
                .id(10L)
                .commissionRate(new BigDecimal("10.00"))
                .build();
    }

    @Test
    @DisplayName("Role boundary: admin routes require ROLE_ADMIN")
    void roleBoundaries() {
        when(platformConfigService.getCommissionRateDefault()).thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(vendor));

        roleBoundaryTester.assertUnauthenticated("/api/v1/admin/platform-settings/commission-rate", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/admin/platform-settings/commission-rate", HttpMethod.GET, 403, 403, 200);

        roleBoundaryTester.assertUnauthenticated("/api/v1/admin/vendors/1/commission-rate", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/admin/vendors/1/commission-rate", HttpMethod.GET, 403, 403, 200);
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Read platform commission rate default")
    void getPlatformRate() throws Exception {
        when(platformConfigService.getCommissionRateDefault()).thenReturn(new BigDecimal("10.00"));

        mockMvc.perform(get("/api/v1/admin/platform-settings/commission-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(10.00));
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Update platform commission rate default successfully")
    void updatePlatformRate() throws Exception {
        when(userRepository.findByEmailWithRole("admin@flowerconnect.com")).thenReturn(Optional.of(admin));

        AdminCommissionRateRequest request = AdminCommissionRateRequest.builder()
                .rate(new BigDecimal("12.50"))
                .reason("Seasonal revision")
                .build();

        mockMvc.perform(patch("/api/v1/admin/platform-settings/commission-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(12.50));

        verify(platformConfigService).updateCommissionRateDefault(eq(1L), eq("Seasonal revision"), eq(new BigDecimal("12.50")));
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Read vendor commission rate override")
    void getVendorRate() throws Exception {
        when(vendorProfileRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(vendor));

        mockMvc.perform(get("/api/v1/admin/vendors/10/commission-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(10.00));
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Read vendor commission rate returns 404 when vendor missing")
    void getVendorRateNotFound() throws Exception {
        when(vendorProfileRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/admin/vendors/999/commission-rate"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Set vendor commission rate override and record audit row")
    void setVendorRate() throws Exception {
        when(userRepository.findByEmailWithRole("admin@flowerconnect.com")).thenReturn(Optional.of(admin));
        when(vendorProfileRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(vendor));

        AdminCommissionRateRequest request = AdminCommissionRateRequest.builder()
                .rate(new BigDecimal("15.00"))
                .reason("High-volume partner tier")
                .build();

        mockMvc.perform(patch("/api/v1/admin/vendors/10/commission-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(15.00));

        verify(vendorProfileRepository).saveAndFlush(vendor);
        assertThat(vendor.getCommissionRate()).isEqualByComparingTo(new BigDecimal("15.00"));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActor()).isEqualTo(admin);
        assertThat(audit.getActionType()).isEqualTo(AdminCommissionRateController.ACTION_VENDOR_COMMISSION_RATE_CHANGED);
        assertThat(audit.getEntityType()).isEqualTo(AdminCommissionRateController.ENTITY_TYPE_VENDOR_PROFILE);
        assertThat(audit.getEntityId()).isEqualTo(10L);
        assertThat(audit.getPreviousValue()).isEqualTo("10.00");
        assertThat(audit.getNewValue()).isEqualTo("15.00");
        assertThat(audit.getReason()).isEqualTo("High-volume partner tier");
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Clear vendor commission rate override and record audit row")
    void clearVendorRate() throws Exception {
        when(userRepository.findByEmailWithRole("admin@flowerconnect.com")).thenReturn(Optional.of(admin));
        when(vendorProfileRepository.findByIdWithDetails(10L)).thenReturn(Optional.of(vendor));

        mockMvc.perform(delete("/api/v1/admin/vendors/10/commission-rate"))
                .andExpect(status().isNoContent());

        verify(vendorProfileRepository).saveAndFlush(vendor);
        assertThat(vendor.getCommissionRate()).isNull();

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActor()).isEqualTo(admin);
        assertThat(audit.getActionType()).isEqualTo(AdminCommissionRateController.ACTION_VENDOR_COMMISSION_RATE_CLEARED);
        assertThat(audit.getPreviousValue()).isEqualTo("10.00");
        assertThat(audit.getNewValue()).isNull();
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Validation: rate < 0.00 returns 400")
    void negativeRateValidation() throws Exception {
        AdminCommissionRateRequest request = AdminCommissionRateRequest.builder()
                .rate(new BigDecimal("-1.00"))
                .build();

        mockMvc.perform(patch("/api/v1/admin/platform-settings/commission-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Validation: rate > 100.00 returns 400")
    void overHundredRateValidation() throws Exception {
        AdminCommissionRateRequest request = AdminCommissionRateRequest.builder()
                .rate(new BigDecimal("100.01"))
                .build();

        mockMvc.perform(patch("/api/v1/admin/platform-settings/commission-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@flowerconnect.com", roles = {"ADMIN"})
    @DisplayName("Validation: rate with > 2 decimal places returns 400")
    void scaleValidation() throws Exception {
        AdminCommissionRateRequest request = AdminCommissionRateRequest.builder()
                .rate(new BigDecimal("10.125"))
                .build();

        mockMvc.perform(patch("/api/v1/admin/platform-settings/commission-rate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
