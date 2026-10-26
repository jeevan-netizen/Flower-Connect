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

package com.flowerconnect.billing.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.billing.dto.AdminCommissionRateRequest;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class CommissionRateIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private VendorProfileRepository vendorProfileRepository;
    @Autowired
    private ServiceLocationRepository serviceLocationRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User adminUser;
    private String adminToken;
    private User vendorUser;
    private VendorProfile vendorProfile;
    private String vendorToken;

    @BeforeEach
    void setUp() throws Exception {
        adminUser = createUser("admin-rate-" + UUID.randomUUID() + "@test.com", PASSWORD, "ADMIN");
        adminToken = tokenFor(adminUser);

        vendorUser = createUser("vendor-rate-" + UUID.randomUUID() + "@test.com", PASSWORD, "FLORIST");
        vendorToken = tokenFor(vendorUser);

        ServiceLocation location = serviceLocationRepository.findAll().stream().findFirst()
                .orElseGet(() -> serviceLocationRepository.saveAndFlush(ServiceLocation.builder()
                        .city("Bengaluru")
                        .area("Indiranagar")
                        .pincode("560038")
                        .timezone("Asia/Kolkata")
                        .latitude(BigDecimal.valueOf(12.97))
                        .longitude(BigDecimal.valueOf(77.64))
                        .build()));

        vendorProfile = vendorProfileRepository.saveAndFlush(VendorProfile.builder()
                .user(vendorUser)
                .businessName("Integration Petals " + UUID.randomUUID())
                .addressLine1("100 Indiranagar Main")
                .serviceLocation(location)
                .latitude(BigDecimal.valueOf(12.97))
                .longitude(BigDecimal.valueOf(77.64))
                .deliveryRadiusKm(new BigDecimal("10.00"))
                .status(VendorProfile.Status.APPROVED)
                .commissionRate(null) // No initial override
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build());
    }

    @Test
    @DisplayName("End-to-end: Read default -> Set override -> Read effective -> Clear override -> Audit verification")
    void fullCommissionRateLifecycle() throws Exception {
        // 1. Read platform default (seeded as 10.00)
        mockMvc.perform(get("/api/v1/admin/platform-settings/commission-rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(10.00));

        // 2. Vendor has no override -> effective rate is platform default (10.00)
        mockMvc.perform(get("/api/v1/vendor/billing/rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(10.00));

        // 3. Admin sets vendor override to 14.50
        AdminCommissionRateRequest setRequest = AdminCommissionRateRequest.builder()
                .rate(new BigDecimal("14.50"))
                .reason("Negotiated VIP vendor contract")
                .build();

        mockMvc.perform(patch("/api/v1/admin/vendors/" + vendorProfile.getId() + "/commission-rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(setRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(14.50));

        // Verify DB row
        VendorProfile reloaded = vendorProfileRepository.findById(vendorProfile.getId()).orElseThrow();
        assertThat(reloaded.getCommissionRate()).isEqualByComparingTo(new BigDecimal("14.50"));

        // Verify audit log for override
        List<AuditLog> setAudits = auditLogRepository.findAll().stream()
                .filter(a -> "VENDOR_COMMISSION_RATE_CHANGED".equals(a.getActionType())
                        && vendorProfile.getId().equals(a.getEntityId()))
                .toList();
        assertThat(setAudits).isNotEmpty();
        AuditLog setAudit = setAudits.get(setAudits.size() - 1);
        assertThat(setAudit.getActor().getId()).isEqualTo(adminUser.getId());
        assertThat(setAudit.getPreviousValue()).isNull();
        assertThat(setAudit.getNewValue()).isEqualTo("14.50");
        assertThat(setAudit.getReason()).isEqualTo("Negotiated VIP vendor contract");

        // 4. Vendor reads new effective rate -> 14.50
        mockMvc.perform(get("/api/v1/vendor/billing/rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(14.50));

        // 5. Admin clears vendor override
        mockMvc.perform(delete("/api/v1/admin/vendors/" + vendorProfile.getId() + "/commission-rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        // Verify DB row is null
        reloaded = vendorProfileRepository.findById(vendorProfile.getId()).orElseThrow();
        assertThat(reloaded.getCommissionRate()).isNull();

        // Verify audit log for clear
        List<AuditLog> clearAudits = auditLogRepository.findAll().stream()
                .filter(a -> "VENDOR_COMMISSION_RATE_CLEARED".equals(a.getActionType())
                        && vendorProfile.getId().equals(a.getEntityId()))
                .toList();
        assertThat(clearAudits).isNotEmpty();
        AuditLog clearAudit = clearAudits.get(clearAudits.size() - 1);
        assertThat(clearAudit.getActor().getId()).isEqualTo(adminUser.getId());
        assertThat(clearAudit.getPreviousValue()).isEqualTo("14.50");
        assertThat(clearAudit.getNewValue()).isNull();

        // 6. Vendor reads effective rate -> back to platform default (10.00)
        mockMvc.perform(get("/api/v1/vendor/billing/rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(10.00));

        // 7. Admin updates platform default to 11.00
        AdminCommissionRateRequest updateDefaultReq = AdminCommissionRateRequest.builder()
                .rate(new BigDecimal("11.00"))
                .reason("Platform wide fee increase")
                .build();

        mockMvc.perform(patch("/api/v1/admin/platform-settings/commission-rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDefaultReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(11.00));

        // 8. Vendor reads effective rate -> now 11.00
        mockMvc.perform(get("/api/v1/vendor/billing/rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(vendorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(11.00));

        // Restore platform default to 10.00
        AdminCommissionRateRequest restoreReq = AdminCommissionRateRequest.builder()
                .rate(new BigDecimal("10.00"))
                .reason("Restore default")
                .build();
        mockMvc.perform(patch("/api/v1/admin/platform-settings/commission-rate")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restoreReq)))
                .andExpect(status().isOk());
    }

    private User createUser(String email, String password, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .fullName("Test " + roleName)
                .phone("+91" + randomDigits(10))
                .role(role)
                .status(User.Status.ACTIVE)
                .build());
    }

    private String tokenFor(User user) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LoginRequest.builder()
                                .email(user.getEmail())
                                .password(PASSWORD)
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        AuthResponse auth = objectMapper.readValue(
                result.getResponse().getContentAsString(), AuthResponse.class);
        assertNotNull(auth.getAccessToken());
        return auth.getAccessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private static String randomDigits(int count) {
        StringBuilder digits = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            digits.append((int) (Math.random() * 10));
        }
        return digits.toString();
    }
}
