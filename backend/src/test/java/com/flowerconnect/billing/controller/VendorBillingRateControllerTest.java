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

import com.flowerconnect.billing.service.CommissionRateService;
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
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = VendorBillingRateController.class)
@Import({VendorBillingRateControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorBillingRateControllerTest {

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
    private CommissionRateService commissionRateService;

    private RoleBoundaryTester roleBoundaryTester;
    private User vendorUser;
    private VendorProfile vendor;

    @BeforeEach
    void setUp() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);
        vendorUser = User.builder()
                .id(2L)
                .email("test-florist@test.com")
                .role(Role.builder().name("FLORIST").build())
                .build();
        vendor = VendorProfile.builder()
                .id(20L)
                .user(vendorUser)
                .commissionRate(new BigDecimal("15.00"))
                .build();
    }

    @Test
    @DisplayName("Role boundary: vendor billing rate requires ROLE_FLORIST")
    void roleBoundaries() {
        when(userRepository.findByEmailWithRole("test-florist@test.com")).thenReturn(Optional.of(vendorUser));
        when(vendorProfileRepository.findByUserEmail("test-florist@test.com")).thenReturn(Optional.of(vendor));
        when(commissionRateService.resolveEffectivePercentage(vendor)).thenReturn(new BigDecimal("15.00"));

        roleBoundaryTester.assertUnauthenticated("/api/v1/vendor/billing/rate", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles("/api/v1/vendor/billing/rate", HttpMethod.GET, 403, 200, 403);
    }

    @Test
    @WithMockUser(username = "florist@flowerconnect.com", roles = {"FLORIST"})
    @DisplayName("Vendor reads own effective commission rate")
    void vendorReadsEffectiveRate() throws Exception {
        User user = User.builder()
                .id(3L)
                .email("florist@flowerconnect.com")
                .role(Role.builder().name("FLORIST").build())
                .build();
        when(userRepository.findByEmailWithRole("florist@flowerconnect.com")).thenReturn(Optional.of(user));
        when(vendorProfileRepository.findByUserEmail("florist@flowerconnect.com")).thenReturn(Optional.of(vendor));
        when(commissionRateService.resolveEffectivePercentage(vendor)).thenReturn(new BigDecimal("15.00"));

        mockMvc.perform(get("/api/v1/vendor/billing/rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(15.00));
    }

    @Test
    @WithMockUser(username = "unknown-florist@test.com", roles = {"FLORIST"})
    @DisplayName("Vendor returns 404 when vendor profile missing")
    void vendorNotFoundReturns404() throws Exception {
        User user = User.builder()
                .id(99L)
                .email("unknown-florist@test.com")
                .role(Role.builder().name("FLORIST").build())
                .build();
        when(userRepository.findByEmailWithRole("unknown-florist@test.com")).thenReturn(Optional.of(user));
        when(vendorProfileRepository.findByUserEmail("unknown-florist@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/vendor/billing/rate"))
                .andExpect(status().isNotFound());
    }
}
